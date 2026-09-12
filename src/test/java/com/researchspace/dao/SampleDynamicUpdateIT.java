package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.SampleSiblingRowLock;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A recomputed total must not revert another user's concurrent edit to the same sample row.
 *
 * <p>{@code lockSiblingRowsAndRecalculateTotal} reads the sample UNLOCKED on purpose: taking its
 * row lock would insert a sample-before-subsample acquisition into paths that lock subsample rows
 * first. It then assigns the recomputed total and saves. Without {@code @DynamicUpdate} that flush
 * writes EVERY column from this transaction's snapshot, so a rename committed in between is
 * silently reverted, and nothing catches it: {@code SampleEntity.version} is the user-visible
 * version in the global id, not a JPA {@code @Version}, so there is no optimistic locking (parallel
 * review, P3).
 *
 * <p>Needs two committed transactions to show, hence an IT: the revert is invisible inside one
 * session. The competing write goes through a raw connection so it is genuinely committed by
 * another party before this transaction flushes.
 *
 * <p>NOT RUN as part of the changes that added these tests (the plan's own instruction, and the
 * second test was added under the same rule). Running them wipes the dev database: {@code mvn
 * verify -Denvironment=drop-recreate-db -DskipUnitTests=true -Dtest=SampleDynamicUpdateIT
 * -Dsurefire.failIfNoSpecifiedTests=false}.
 */
public class SampleDynamicUpdateIT extends RealTransactionSpringTestBase {

  private @Autowired SampleSiblingRowLock siblingRowLock;

  @Test
  public void aRecomputedTotalDoesNotRevertAConcurrentRename() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long sampleId = sample.getId();

    openTransaction();
    try {
      // The unlocked read that loads this transaction's snapshot of the sample, name included.
      siblingRowLock.lockSiblingRowsAndRecalculateTotal(sampleId);

      // Another party renames and COMMITS while this transaction is still open. The sample row is
      // deliberately not locked, so this succeeds rather than waiting.
      try (Connection other = dataSource.getConnection()) {
        other.setAutoCommit(true);
        try (Statement statement = other.createStatement()) {
          statement.executeUpdate(
              "update Sample set name = 'renamed by someone else' where id = " + sampleId);
        }
      }

      // A second recompute in this same transaction: the flush at commit writes the total. With a
      // full-row UPDATE it would also write the stale name back over the rename.
      siblingRowLock.lockSiblingRowsAndRecalculateTotal(sampleId);
    } finally {
      commitTransaction();
    }

    assertEquals(
        "renamed by someone else",
        nameOf(sampleId),
        "a rename committed between the unlocked read and the flush must survive the recompute");
  }

  /**
   * The same guarantee for the SubSample row, whose window is the larger of the two.
   *
   * <p>{@code registerApiSubSampleUsage} loads the subsample, WAITS on the sibling-set lock, then
   * re-reads it through {@code lockSubSampleForEdit} - which Hibernate serves from the persistence
   * context, so the entity still carries the pre-wait snapshot of every column. The method reads
   * quantity and version as scalars under the lock precisely because of that, but nothing does so
   * for name. Without {@code @DynamicUpdate} on {@code SubSample}, {@code setQuantity} dirties the
   * entity and the flush writes the stale name back over a concurrent rename (parallel review, A7).
   *
   * <p>The rename commits while this transaction holds the subsample's row lock, so it is applied
   * through the same locked row the decrement will write - which is the realistic race: the lock
   * makes the other party wait, it does not make this transaction's cached entity current.
   */
  @Test
  public void aStockDecrementDoesNotRevertAConcurrentRenameOfTheSubSample() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long subSampleId = sample.getSubSamples().get(0).getId();

    // Loads the subsample into this session, so the entity below carries a pre-rename snapshot.
    subSampleApiMgr.getApiSubSampleById(subSampleId, user);

    try (Connection other = dataSource.getConnection()) {
      other.setAutoCommit(true);
      try (Statement statement = other.createStatement()) {
        statement.executeUpdate(
            "update SubSample set name = 'renamed by someone else' where id = " + subSampleId);
      }
    }

    subSampleApiMgr.registerApiSubSampleUsage(
        subSampleId, QuantityInfo.of(BigDecimal.ONE, RSUnitDef.MILLI_LITRE), user);

    assertEquals(
        "renamed by someone else",
        nameOf("SubSample", subSampleId),
        "a rename committed before the decrement flushed must survive it");
  }

  private String nameOf(Long sampleId) throws SQLException {
    return nameOf("Sample", sampleId);
  }

  private String nameOf(String table, Long id) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery("select name from " + table + " where id = " + id)) {
      rows.next();
      return rows.getString(1);
    }
  }
}
