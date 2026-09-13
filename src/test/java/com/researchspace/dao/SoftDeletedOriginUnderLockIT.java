package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.InventoryEditConflictException;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.UnexpectedRollbackException;

/**
 * A stock writer must not act on an origin that was soft-deleted while it queued for the lock, and
 * must not write the row back as active.
 *
 * <p>The lock serialises; it does not refresh. {@code performOperation} loads each origin before
 * acquiring any lock, and {@code GenericDaoHibernate.lockRowForUpdate} then returns {@code
 * session.get(...)}, which hands back that same managed instance. So the permission assertion made
 * UNDER the lock reads the pre-lock {@code deleted} flag, and {@code getQuantityForUpdate} - which
 * has no {@code deleted} filter - still returns the row's retained quantity. The operation
 * therefore proceeds, and because the subsample row is written with a full-row UPDATE
 * (deliberately, so the deduction lands; see SampleDynamicUpdateIT) the flush writes {@code deleted
 * = false} back, resurrecting the deleted origin while creating derived material from it (Codex
 * review, P1).
 *
 * <p>That is a different class of problem from the field-level last-write-wins {@code
 * GenericDao.lockRowForUpdate} documents and adr/0007 accepts: a reverted name is a stale value, a
 * resurrected record is a deleted thing coming back holding stock.
 *
 * <p>Needs two committed transactions, hence an IT. Running it wipes the dev database: {@code mvn
 * verify -Denvironment=drop-recreate-db -DskipUnitTests=true -Dtest=SoftDeletedOriginUnderLockIT
 * -Dsurefire.failIfNoSpecifiedTests=false}.
 */
public class SoftDeletedOriginUnderLockIT extends RealTransactionSpringTestBase {

  @Test
  public void aDecrementIsRefusedWhenTheOriginWasDeletedWhileThisRequestQueued() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long subSampleId = sample.getSubSamples().get(0).getId();

    openTransaction();
    try {
      // Loads the entity into THIS session, so its cached deleted flag says false.
      subSampleApiMgr.getApiSubSampleById(subSampleId, user);

      // Another party soft-deletes it and COMMITS, as one can while this request queues for the
      // sibling-set lock. Soft deletion keeps the row and its quantity, which is why the scalar
      // read under the lock still returns a usable number.
      softDeleteFromAnotherConnection(subSampleId);

      assertThrows(
          InventoryEditConflictException.class,
          () ->
              subSampleApiMgr.registerApiSubSampleUsage(
                  subSampleId, QuantityInfo.of(BigDecimal.ONE, RSUnitDef.GRAM), user),
          "the origin was deleted before this decrement ran, so it must be refused rather than"
              + " acted on from a stale cached entity");
    } finally {
      // The refusal marks the transaction rollback-only, which is the point: nothing it did is
      // committed, so the commit reports the rollback instead of succeeding. Swallowed rather than
      // asserted, so that a regression in the refusal above fails on ITS assertion and on the
      // deleted flag below, rather than being masked by this one.
      try {
        commitTransaction();
      } catch (UnexpectedRollbackException rolledBackByTheRefusal) {
        // expected; see above
      }
    }

    assertEquals(
        1,
        deletedFlagOf(subSampleId),
        "the deleted origin must still be deleted: a full-row write from the pre-lock snapshot"
            + " would resurrect it");
  }

  private void softDeleteFromAnotherConnection(Long subSampleId) throws SQLException {
    try (Connection other = dataSource.getConnection()) {
      other.setAutoCommit(true);
      try (Statement statement = other.createStatement()) {
        statement.executeUpdate("update SubSample set deleted = 1 where id = " + subSampleId);
      }
    }
  }

  private int deletedFlagOf(Long subSampleId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery("select deleted from SubSample where id = " + subSampleId)) {
      rows.next();
      return rows.getInt(1);
    }
  }
}
