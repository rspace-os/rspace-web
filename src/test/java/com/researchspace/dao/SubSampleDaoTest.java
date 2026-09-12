package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The operations endpoint serialises concurrent work on one origin by locking its row through
 * {@link com.researchspace.dao.GenericDao#lockRowForUpdate} and then reads the values it computes
 * from as scalars under that lock (code review, findings 1 and 2). The lock's scope (one row, one
 * table, blocking a second connection) is only observable across transactions and is asserted in
 * {@code GenericDaoLockScopeIT}; the value behaviour of the scalar reads is pinned here, along with
 * the cheap guard that each of them still reaches the database as a {@code FOR UPDATE} at all.
 */
public class SubSampleDaoTest extends SpringTransactionalTest {

  private @Autowired SubSampleDao subSampleDao;

  private Long persistSubSampleHolding(String value, RSUnitDef unit) {
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("subsample scalar test", user);
    SubSample subSample = sample.getSubSamples().get(0);
    subSample.moveToNewParent(workbench);
    subSample.setQuantity(new QuantityInfo(new BigDecimal(value), unit.getId()));
    Long subSampleId = sampleDao.persistNewSample(sample).getSubSamples().get(0).getId();
    // flush the insert, then clear, so the reads below hit the row rather than the session cache
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();
    return subSampleId;
  }

  /**
   * A persisted row whose quantityNumericValue column is NULL, the "no quantity" state {@code
   * getQuantityForUpdate} guards for (quantityUnitId is NOT NULL in the schema, so only the value
   * column can be missing). The entity setter forbids null ({@code SubSample.setQuantity}), so the
   * column is nulled with a bulk update that bypasses it.
   */
  private Long persistSubSampleWithNullQuantityValueColumn() {
    Long subSampleId = persistSubSampleHolding("1", RSUnitDef.MILLI_LITRE);
    sessionFactory
        .getCurrentSession()
        .createMutationQuery(
            "update SubSample ss set ss.quantityInfo.numericValue = null where ss.id = :id")
        .setParameter("id", subSampleId)
        .executeUpdate();
    return subSampleId;
  }

  @Test
  public void getQuantityForUpdateReturnsTheStoredQuantity() {
    Long subSampleId = persistSubSampleHolding("5.5", RSUnitDef.MILLI_LITRE);

    QuantityInfo quantity = subSampleDao.getQuantityForUpdate(subSampleId);

    assertEquals(0, new BigDecimal("5.5").compareTo(quantity.getNumericValue()));
    assertEquals(Integer.valueOf(RSUnitDef.MILLI_LITRE.getId()), quantity.getUnitId());
  }

  @Test
  public void getQuantityForUpdateReturnsNullForMissingRowOrMissingQuantity() {
    // callers lock and 404-check the subsample first, so null unambiguously means "holds nothing"
    assertNull(subSampleDao.getQuantityForUpdate(-1L));
    assertNull(subSampleDao.getQuantityForUpdate(persistSubSampleWithNullQuantityValueColumn()));
  }

  @Test
  public void getActiveQuantitiesForUpdateReturnsTheSiblingQuantities() {
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("sibling scalar test", user);
    SubSample first = sample.getSubSamples().get(0);
    first.moveToNewParent(workbench);
    first.setQuantity(new QuantityInfo(new BigDecimal("2"), RSUnitDef.MILLI_LITRE.getId()));
    SubSample second = recordFactory.createSubSample("second sibling", user, sample);
    second.moveToNewParent(workbench);
    second.setQuantity(new QuantityInfo(new BigDecimal("3"), RSUnitDef.MILLI_LITRE.getId()));
    sample.getSubSamples().add(second);
    Long sampleId = sampleDao.persistNewSample(sample).getId();
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    BigDecimal total =
        subSampleDao.getActiveQuantitiesForUpdate(sampleId, false).stream()
            .map(QuantityInfo::getNumericValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    assertEquals(0, new BigDecimal("5").compareTo(total));
  }

  /*
   * Every *ForUpdate read must actually reach the database as a FOR UPDATE statement.
   *
   * These queries select no entity, only scalars and embeddable columns, and JPA defines
   * setLockMode in terms of the entities a query RETURNS - so whether Hibernate appends the
   * dialect's locking clause here is implementation behaviour, not specified behaviour. Nothing
   * else in this tier can see it: a single session holds no observable lock, so if the clause were
   * silently dropped these tests would still return the right numbers and pass, while
   * lockSiblingRowsAndRecalculateTotal quietly stopped serialising anything (parallel review, P1).
   *
   * This is the cheap guard, and it pins the SQL rather than the locking behaviour: it is what
   * fails fast on a Hibernate upgrade. The behaviour itself, that the rows are genuinely held
   * against another connection, is pinned in GenericDaoLockScopeIT.
   */

  @Test
  public void everyForUpdateReadEmitsAForUpdateStatement() {
    Long subSampleId = persistSubSampleHolding("5.5", RSUnitDef.MILLI_LITRE);
    Long sampleId = subSampleDao.get(subSampleId).getSample().getId();

    assertEmitsForUpdate(
        "getQuantityForUpdate", () -> subSampleDao.getQuantityForUpdate(subSampleId));
    assertEmitsForUpdate(
        "getVersionForUpdate", () -> subSampleDao.getVersionForUpdate(subSampleId));
    assertEmitsForUpdate(
        "getActiveQuantitiesForUpdate",
        () -> subSampleDao.getActiveQuantitiesForUpdate(sampleId, false));
    // The control: lockRowForUpdate is the same scalar-projection pattern and is independently
    // proven to lock in GenericDaoLockScopeIT, so if this one ever fails the capture below is
    // broken rather than the query.
    assertEmitsForUpdate("lockRowForUpdate", () -> subSampleDao.lockRowForUpdate(subSampleId));
  }

  /** Runs the read and asserts at least one statement Hibernate issued carries "for update". */
  private void assertEmitsForUpdate(String description, Runnable read) {
    List<String> statements = captureSql(read);
    assertTrue(
        statements.stream().anyMatch(sql -> sql.toLowerCase().contains("for update")),
        () -> description + " must reach the database as a FOR UPDATE, issued: " + statements);
  }

  /**
   * The SQL Hibernate issues while running the given work, read off the org.hibernate.SQL logger.
   * The level is raised for the duration because the suite runs Hibernate above DEBUG, and the
   * session is flushed and cleared first so the capture holds this read's statements rather than a
   * pending insert's.
   */
  private List<String> captureSql(Runnable work) {
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    List<String> statements = new ArrayList<>();
    AbstractAppender capture =
        new AbstractAppender("forUpdateSqlCapture", null, null, true, Property.EMPTY_ARRAY) {
          @Override
          public void append(LogEvent event) {
            statements.add(event.getMessage().getFormattedMessage());
          }
        };
    capture.start();
    Logger sqlLogger = (Logger) LogManager.getLogger("org.hibernate.SQL");
    Level original = sqlLogger.getLevel();
    sqlLogger.addAppender(capture);
    sqlLogger.setLevel(Level.DEBUG);
    try {
      work.run();
    } finally {
      sqlLogger.setLevel(original);
      sqlLogger.removeAppender(capture);
      capture.stop();
    }
    return statements;
  }
}
