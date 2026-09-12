package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Characterises the SCOPE of the row locks the Inventory operations endpoint depends on: exclusive
 * on exactly the rows intended, in exactly one table (code review; RSDEV-1231).
 *
 * <p>The regression the first test pins: locking a loaded entity makes Hibernate emit the entity's
 * eager-fetch SELECT (a ~20-table join for a subsample) with {@code FOR UPDATE} on the end, taking
 * {@code lock_mode X} on rows in every joined table (measured: "mysql tables in use 21, locked
 * 20"), including the subsample's parent {@code Container}. That deadlocked inventory operations
 * against unrelated features.
 *
 * <p>The second test pins the sibling-set lock, which is the one the whole design rests on and the
 * only lock with no other source: {@code SampleApiManagerImpl.recalculateTotalFromLockedRows} reads
 * the sample UNLOCKED on purpose and states that "the serialisation this method needs comes from
 * the locked scalar read of the subsample rows below". That read selects no entity, only two
 * embeddable columns, and JPA defines {@code setLockMode} in terms of the entities a query returns,
 * so whether Hibernate emits {@code for update} at all is implementation behaviour rather than
 * specified behaviour. If it does not, no sibling row is locked, the ascending-order deadlock
 * avoidance silently stops working, and concurrent operations lose updates to {@code
 * Sample.totalQuantity} - with every unit and Spring test still green, because a single session
 * cannot observe a lock (parallel review, P1).
 *
 * <p>Lock scope is only observable across connections, so these tests hold the lock in one
 * transaction and probe from a raw second connection; the single-connection value behaviour of the
 * scalar reads is pinned in {@code SubSampleDaoTest}, which also asserts the generated SQL.
 */
public class GenericDaoLockScopeIT extends RealTransactionSpringTestBase {

  private @Autowired SubSampleDao subSampleDao;

  @Test
  public void lockRowForUpdateLocksOnlyTheSubSampleRowNotItsFetchGraph() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long subSampleId = sample.getSubSamples().get(0).getId();
    Long sampleId = sample.getId();
    Long userId = user.getId();
    // the subsample's workbench: a row the old graph lock held via the eager-fetch join
    Long workbenchId = getWorkbenchForUser(user).getId();

    openTransaction();
    try {
      subSampleDao.lockRowForUpdate(subSampleId);

      // Probed from a raw second connection rather than information_schema.innodb_trx (whose
      // trx_tables_locked count needs the PROCESS privilege the app's DB user does not have).
      // A row is free exactly when another transaction can take FOR UPDATE on it within the
      // 1s lock wait; the old graph lock held all four rows probed here.
      try (Connection secondConnection = dataSource.getConnection()) {
        secondConnection.setAutoCommit(false);
        try (Statement statement = secondConnection.createStatement()) {
          statement.execute("set session innodb_lock_wait_timeout = 1");
          // Narrowness: rows the old eager-fetch join locked alongside the subsample must be free.
          assertRowLockable(statement, "Container", workbenchId);
          assertRowLockable(statement, "Sample", sampleId);
          assertRowLockable(statement, "User", userId);
          // Effectiveness: the subsample's own row must be held, so the same probe times out.
          assertThrows(
              SQLException.class,
              () ->
                  statement.executeQuery(
                      "select id from SubSample where id = " + subSampleId + " for update"),
              "the locked subsample row should not be lockable from a second connection");
        } finally {
          secondConnection.rollback();
        }
      }
    } finally {
      commitTransaction();
    }
  }

  @Test
  public void getActiveQuantitiesForUpdateLocksEverySiblingRowAndNothingElse() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createSampleWithTwoSubSamples(user, "sibling lock test");
    Long sampleId = sample.getId();
    Long firstSiblingId = sample.getSubSamples().get(0).getId();
    Long secondSiblingId = sample.getSubSamples().get(1).getId();
    // Another sample's subsample: the lock must not reach beyond the sample it was asked about.
    Long unrelatedSubSampleId =
        createBasicSampleForUser(user, "unrelated sample").getSubSamples().get(0).getId();

    openTransaction();
    try {
      subSampleDao.getActiveQuantitiesForUpdate(sampleId, false);

      try (Connection secondConnection = dataSource.getConnection()) {
        secondConnection.setAutoCommit(false);
        try (Statement statement = secondConnection.createStatement()) {
          statement.execute("set session innodb_lock_wait_timeout = 1");
          // Effectiveness: EVERY sibling is held, not merely the first row the query returned.
          // This is what makes a second operation on a sibling wait here rather than deadlock
          // later against a per-origin lock.
          assertRowNotLockable(statement, "SubSample", firstSiblingId);
          assertRowNotLockable(statement, "SubSample", secondSiblingId);
          // Narrowness: another sample's subsample is untouched, so the lock does not serialise
          // unrelated work.
          assertRowLockable(statement, "SubSample", unrelatedSubSampleId);
          // The parent Sample row stays free, which recalculateTotalFromLockedRows relies on: it
          // reads the sample unlocked precisely so it does not insert a sample-before-subsample
          // acquisition into paths that lock subsample rows first.
          assertRowLockable(statement, "Sample", sampleId);
        } finally {
          secondConnection.rollback();
        }
      }
    } finally {
      commitTransaction();
    }
  }

  /** A sample holding two subsamples, so the sibling-set lock has more than one row to take. */
  private ApiSampleWithFullSubSamples createSampleWithTwoSubSamples(User user, String sampleName) {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName(sampleName);
    newSample.setSubSamples(List.of(subSampleHolding("first", 2), subSampleHolding("second", 3)));
    return sampleApiMgr.createNewApiSample(newSample, user);
  }

  private static ApiSubSample subSampleHolding(String name, int grams) {
    ApiSubSample subSample = new ApiSubSample();
    subSample.setName(name);
    subSample.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(grams), RSUnitDef.GRAM));
    return subSample;
  }

  private static void assertRowLockable(Statement statement, String table, Long id)
      throws SQLException {
    try (ResultSet row =
        statement.executeQuery("select id from " + table + " where id = " + id + " for update")) {
      assertTrue(row.next(), table + " row should exist and be lockable by another transaction");
    }
  }

  private static void assertRowNotLockable(Statement statement, String table, Long id) {
    assertThrows(
        SQLException.class,
        () ->
            statement.executeQuery("select id from " + table + " where id = " + id + " for update"),
        () ->
            "the locked "
                + table
                + " row "
                + id
                + " should not be lockable from a second connection");
  }
}
