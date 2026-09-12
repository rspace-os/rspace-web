package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.service.inventory.SampleSiblingRowLock;
import com.researchspace.testutils.RealTransactionSpringTestBase;
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
 * <p>NOT RUN as part of the change that added it (the plan's own instruction). Running it wipes the
 * dev database: {@code mvn verify -Denvironment=drop-recreate-db -DskipUnitTests=true
 * -Dtest=SampleDynamicUpdateIT -Dsurefire.failIfNoSpecifiedTests=false}.
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

  private String nameOf(Long sampleId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery("select name from Sample where id = " + sampleId)) {
      rows.next();
      return rows.getString(1);
    }
  }
}
