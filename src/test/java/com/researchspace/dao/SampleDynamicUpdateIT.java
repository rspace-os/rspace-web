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
   * SubSample does NOT get the same treatment, and this records why rather than leaving it to look
   * like an oversight.
   *
   * <p>A stock decrement writes the subsample row from an entity whose loaded state is the pre-lock
   * snapshot. Giving {@code SubSample} {@code @DynamicUpdate} would stop the full-row write from
   * reverting a concurrent rename - and would also stop the deduction itself whenever its result
   * happened to equal the cached quantity, which the test below proves and which is silent stock
   * loss (Codex review, P1). A reverted name is the field-level last-write-wins {@code
   * GenericDao.lockRowForUpdate} documents and DevDocs/adr/0007 accepts for every unlocked write
   * path; a lost deduction is not.
   *
   * <p>So the rename does NOT survive here, deliberately. Asserted so that anyone who changes it
   * has to read the test below before deciding the revert is worth fixing this way.
   */
  @Test
  public void aStockDecrementStillOverwritesAConcurrentRenameOfTheSubSample() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long subSampleId = sample.getSubSamples().get(0).getId();
    String originalName = sample.getSubSamples().get(0).getName();

    openTransaction();
    try {
      // Loads the entity into THIS session, so its snapshot carries the original name.
      subSampleApiMgr.getApiSubSampleById(subSampleId, user);

      try (Connection other = dataSource.getConnection()) {
        other.setAutoCommit(true);
        try (Statement statement = other.createStatement()) {
          statement.executeUpdate(
              "update SubSample set name = 'renamed by someone else' where id = " + subSampleId);
        }
      }

      subSampleApiMgr.registerApiSubSampleUsage(
          subSampleId, QuantityInfo.of(BigDecimal.ONE, RSUnitDef.GRAM), user);
    } finally {
      commitTransaction();
    }

    assertEquals(
        originalName,
        nameOf("SubSample", subSampleId),
        "the full-row write that makes the deduction land also writes the snapshot name back; that"
            + " is the accepted norm (adr/0007), and @DynamicUpdate is not an acceptable fix for"
            + " it");
  }

  /**
   * A decrement must reach the database even when its RESULT happens to equal the quantity this
   * transaction cached before it took the lock.
   *
   * <p>This is the trap {@code @DynamicUpdate} sets on this path (Codex review, P1). Hibernate
   * dirty-checks the entity against its LOADED STATE, which is the pre-lock snapshot, not the
   * locked row: the scalar reads that make the arithmetic correct refresh neither. So a request
   * that cached 5 g, waited while another writer committed a top-up to 15 g, then correctly read 15
   * g under the lock and deducted 10 g, assigns 5 g - equal to the cached value. The quantity is
   * not dirty, and a dynamic UPDATE omits the quantity columns entirely. The version and
   * modification date still advance and the operation reports success, but the row stays at 15 g:
   * the stock was never deducted.
   *
   * <p>A full-row UPDATE happened to mask this, because it wrote every column whether dirty or not.
   * That is not a fix to rely on, and it is the same full-row write that reverts a concurrent
   * rename (the two tests above), so the entity is reconciled with the locked row instead.
   *
   * <p>The numbers matter: 15 - 10 == 5 is the whole point. Any other combination leaves the
   * quantity genuinely dirty and passes either way.
   */
  @Test
  public void aStockDecrementAppliesEvenWhenItsResultEqualsTheCachedQuantity() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long subSampleId = sample.getSubSamples().get(0).getId();

    openTransaction();
    try {
      // Loads the entity into THIS session, so its loaded-state snapshot says 5 g.
      subSampleApiMgr.getApiSubSampleById(subSampleId, user);

      // Another party tops it up to 15 g and COMMITS, as one can while this request waits for the
      // sibling-set lock.
      try (Connection other = dataSource.getConnection()) {
        other.setAutoCommit(true);
        try (Statement statement = other.createStatement()) {
          statement.executeUpdate(
              "update SubSample set quantityNumericValue = 15 where id = " + subSampleId);
        }
      }

      subSampleApiMgr.registerApiSubSampleUsage(
          subSampleId, QuantityInfo.of(BigDecimal.TEN, RSUnitDef.GRAM), user);
    } finally {
      commitTransaction();
    }

    assertEquals(
        0,
        new BigDecimal("5").compareTo(quantityOf(subSampleId)),
        "the deduction must reach the row: 15 g committed by another writer, 10 g taken under the"
            + " lock, so 5 g remains. Finding 15 g means the UPDATE omitted the quantity because it"
            + " matched this transaction's stale cached value.");
  }

  private BigDecimal quantityOf(Long subSampleId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows =
            statement.executeQuery(
                "select quantityNumericValue from SubSample where id = " + subSampleId)) {
      rows.next();
      return rows.getBigDecimal(1);
    }
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
