package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The stock-write concurrency matrix: what a writer must do when another party commits a change to
 * the row it loaded, between that load and its own write.
 *
 * <p>Named for the contract rather than for any one endpoint, because the contract is shared. Every
 * stock decrement in the application funnels through {@code
 * SubSampleApiManagerImpl.registerApiSubSampleUsage}, so the operations endpoint, list of materials
 * and stoichiometry all inherit whatever this class pins. The matrix and the holes it does not yet
 * cover are in {@code .claude/plan-concurrency-matrix.md}.
 *
 * <p>The shape of every test here is deliberate and is the reason these are plain {@code *IT} and
 * not MVCIT: load the entity into THIS session to fix the pre-lock snapshot, commit the competing
 * change on a raw second connection so it is genuinely another party, run the writer, then assert
 * the row on a third connection. Two concurrent HTTP requests would only exercise the defect when
 * they happened to interleave, and would report green when they serialised. MVCITs remain as
 * end-to-end backstops; they are not the guard.
 *
 * <p>Sibling cells already covered elsewhere: {@code SampleDynamicUpdateIT} (rename under a
 * decrement, a quantity change whose result equals the cached value, and a rename under the parent
 * total recompute) and {@code SoftDeletedOriginUnderLockIT} (the origin itself soft-deleted).
 *
 * <p>Running any of these wipes the dev database: {@code mvn verify -Denvironment=drop-recreate-db
 * -DskipUnitTests=true -Dtest=StockWriterConcurrencyIT -Dsurefire.failIfNoSpecifiedTests=false}.
 */
public class StockWriterConcurrencyIT extends RealTransactionSpringTestBase {

  private @Autowired ListOfMaterialsApiManager lomManager;
  private @Autowired InventoryMoveHelper moveHelper;
  private @Autowired SubSampleDao subSampleDao;

  /**
   * W1 x M4. The guard that stops a decrement putting its origin back in the container it was moved
   * out of.
   *
   * <p>{@code parentLocation} is a {@code @OneToOne} whose foreign key is a column ON the SubSample
   * row ({@code SubSample.parentLocation_id}), so the full-row UPDATE that makes the deduction land
   * (see {@code SampleDynamicUpdateIT}) writes it from this transaction's pre-lock snapshot,
   * exactly as it writes the name. A reverted name is the field-level last-write-wins that {@code
   * GenericDao.lockRowForUpdate} documents and DevDocs/adr/0007 accepts. A reverted location is
   * not: the record is then filed somewhere it is not.
   *
   * <p>The end-to-end version of this test, running the whole decrement, is what FOUND the defect:
   * it failed with a foreign key violation, because moving out of a list-layout container also
   * drops the vacated {@code ContainerLocation} row, so the restored key pointed at nothing and the
   * constraint took the decrement down inside the transaction. That is why {@code
   * SubSampleDao.refreshParentLocationFromLockedRow} exists.
   *
   * <p>What is asserted here is narrower than that run, deliberately. See the note in {@code
   * .claude/plan-concurrency-matrix.md}: with the fix in place the end-to-end run then fails later,
   * inside Hibernate Search, because this session still holds a proxy to the location the move
   * deleted. Whether that is only reachable from a test that keeps one session open across a
   * committed move, or is a second real consequence of the same race, is NOT settled, and pinning
   * the guard is not the place to pretend it is. This test covers the column; that question is
   * recorded as open rather than asserted either way.
   */
  @Test
  public void theOriginsParentLocationIsReconciledWithTheRowBeforeADecrementWritesIt()
      throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long subSampleId = sample.getSubSamples().get(0).getId();
    ApiContainer destination = createBasicContainerForUser(user, "moved into here");
    Long locationBefore = parentLocationOf(subSampleId);
    assertNotNull(
        locationBefore,
        "precondition: a new subsample is stored in the owner's workbench, so it has a location"
            + " whose id the full-row write could restore");

    openTransaction();
    try {
      // Loads the entity into THIS session, so its snapshot carries the original location. This is
      // the same instance lockRowForUpdate hands back: the lock serialises, it does not refresh.
      SubSample origin = subSampleApiMgr.assertUserCanEditSubSample(subSampleId, user);
      assertEquals(locationBefore, origin.getParentLocation().getId());

      // Another party moves it and COMMITS, as one can while this request queues for the
      // sibling-set lock. Through the real InventoryMoveHelper in its own transaction, so every
      // invariant the move maintains still holds: raw SQL cannot express a move without leaving
      // artifacts (a location row inserted behind a live session's back is orphan-removed, and a
      // cleared foreign key is not a state a subsample may be saved in at all).
      moveInItsOwnCommittedTransaction(subSampleId, destination.getId(), user);
      Long locationAfter = parentLocationOf(subSampleId);
      assertNotEquals(locationBefore, locationAfter, "the competing move must have committed");

      subSampleDao.refreshParentLocationFromLockedRow(origin);

      assertEquals(
          locationAfter,
          origin.getParentLocation().getId(),
          "the entity must carry the committed location before anything writes the row: leaving the"
              + " snapshot value there is what the full-row UPDATE would put back");
    } finally {
      // Nothing here is meant to be written: the reconcile dirtied the entity, and committing would
      // flush it along with everything else this session holds.
      status.setRollbackOnly();
      try {
        commitTransaction();
      } catch (UnexpectedRollbackException deliberate) {
        // expected; see above
      }
    }
  }

  /**
   * W1 x M6. A decrement must be refused when the origin's PARENT SAMPLE was deleted while this
   * request queued.
   *
   * <p>Nothing on the decrement path looks at the parent. The refusal is expected to arrive
   * transitively, because deleting a sample cascades to its subsamples ({@code
   * markSubSampleAsDeleted(.., partOfSampleDeletion = true)}) and the subsample's own deleted flag
   * IS re-read as a locked scalar. That transitivity is the whole content of this test: it was an
   * assumption until it was asserted, and it is the only thing standing between a deleted sample
   * and material being created from its stock.
   */
  @Test
  public void aDecrementIsRefusedWhenTheParentSampleWasDeletedWhileThisRequestQueued()
      throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    Long subSampleId = sample.getSubSamples().get(0).getId();

    openTransaction();
    try {
      subSampleApiMgr.getApiSubSampleById(subSampleId, user);

      // Another party deletes the SAMPLE and COMMITS. Sample deletion cascades to the subsamples,
      // so both rows carry the flag; both are retained with their quantities, which is why the
      // scalar read under the lock still returns a usable number.
      deleteSampleAndItsSubSamplesFromAnotherConnection(sample.getId());

      assertThrows(
          InventoryEditConflictException.class,
          () ->
              subSampleApiMgr.registerApiSubSampleUsage(
                  subSampleId, QuantityInfo.of(BigDecimal.ONE, RSUnitDef.GRAM), user),
          "the parent sample was deleted before this decrement ran, so it must be refused rather"
              + " than acted on from a stale cached entity");
    } finally {
      try {
        commitTransaction();
      } catch (UnexpectedRollbackException rolledBackByTheRefusal) {
        // expected: the refusal marks the transaction rollback-only
      }
    }

    assertEquals(
        1,
        deletedFlagOf("SubSample", subSampleId),
        "the deleted subsample must still be deleted: a full-row write from the pre-lock snapshot"
            + " would resurrect it");
  }

  /**
   * W3 x M2. A list of materials must inherit the same refusal.
   *
   * <p>{@code InventoryMaterialUsageHelper} locks the parent sample sets itself and then calls
   * {@code registerApiSubSampleUsage}, so the refusal reaches it through the shared writer rather
   * than through anything of its own. That inheritance is currently incidental. This pins it, so
   * that a future change which gives list of materials its own decrement path has to fail a test
   * rather than quietly lose the guard.
   */
  @Test
  public void aListOfMaterialsUsageIsRefusedWhenTheSubSampleWasDeletedWhileThisRequestQueued()
      throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "lom host");
    Field field = doc.getFields().get(0);
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    ApiSubSample subSample = sample.getSubSamples().get(0);

    openTransaction();
    try {
      subSampleApiMgr.getApiSubSampleById(subSample.getId(), user);
      softDeleteFromAnotherConnection(subSample.getId());

      ApiListOfMaterials lom = new ApiListOfMaterials();
      lom.setName("takes from a deleted subsample");
      lom.setElnFieldId(field.getId());
      // updateInventoryQuantity = TRUE is what makes this a stock WRITE. With false the list of
      // materials only records the usage and never reaches registerApiSubSampleUsage, so the cell
      // would pass while exercising nothing.
      lom.addMaterialUsage(subSample, subSample.getQuantity(), true);

      assertThrows(
          InventoryEditConflictException.class,
          () -> lomManager.createNewListOfMaterials(lom, user),
          "list of materials decrements through registerApiSubSampleUsage, so it must inherit the"
              + " refusal rather than take stock from a deleted subsample");
    } finally {
      try {
        commitTransaction();
      } catch (UnexpectedRollbackException rolledBackByTheRefusal) {
        // expected: the refusal marks the transaction rollback-only
      }
    }

    assertEquals(
        1,
        deletedFlagOf("SubSample", subSample.getId()),
        "the deleted subsample must still be deleted after the refused usage");
  }

  private void moveInItsOwnCommittedTransaction(Long subSampleId, Long destinationId, User user) {
    TransactionTemplate separateTransaction = new TransactionTemplate(getTxMger());
    separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    separateTransaction.executeWithoutResult(
        committedByAnotherParty -> {
          ApiContainerInfo target = new ApiContainerInfo();
          target.setId(destinationId);
          moveHelper.moveRecordToTargetParentAndLocation(
              subSampleApiMgr.assertUserCanEditSubSample(subSampleId, user), target, null, user);
        });
  }

  private void deleteSampleAndItsSubSamplesFromAnotherConnection(Long sampleId)
      throws SQLException {
    try (Connection other = dataSource.getConnection()) {
      other.setAutoCommit(true);
      try (Statement statement = other.createStatement()) {
        statement.executeUpdate(
            "update SubSample set deleted = 1, deletedOnSampleDeletion = 1 where sample_id = "
                + sampleId);
        statement.executeUpdate("update Sample set deleted = 1 where id = " + sampleId);
      }
    }
  }

  private void softDeleteFromAnotherConnection(Long subSampleId) throws SQLException {
    try (Connection other = dataSource.getConnection()) {
      other.setAutoCommit(true);
      try (Statement statement = other.createStatement()) {
        statement.executeUpdate("update SubSample set deleted = 1 where id = " + subSampleId);
      }
    }
  }

  private Long parentLocationOf(Long subSampleId) throws SQLException {
    return scalar("select parentLocation_id from SubSample where id = " + subSampleId);
  }

  private int deletedFlagOf(String table, Long id) throws SQLException {
    Long flag = scalar("select deleted from " + table + " where id = " + id);
    return flag == null ? 0 : flag.intValue();
  }

  private Long scalar(String sql) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement();
        ResultSet rows = statement.executeQuery(sql)) {
      if (!rows.next()) {
        return null;
      }
      long value = rows.getLong(1);
      return rows.wasNull() ? null : value;
    }
  }
}
