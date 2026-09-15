package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import java.util.List;

/** For DAO operations on Inventory SubSample. */
public interface SubSampleDao extends GenericDao<SubSample, Long> {

  /**
   * Gets subsamples visible to the current user. Optionally, limit to subsamples belonging to
   * particular owner.
   *
   * @param pgCrit
   * @param user
   * @return
   */
  ISearchResults<SubSample> getSubSamplesForUser(
      PaginationCriteria<SubSample> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      User user);

  List<SubSample> getAllUsingImage(FileProperty fileProperty);

  /**
   * The quantities of a sample's active subsamples, read with a row lock ({@code SELECT ... FOR
   * UPDATE}) so the values are current rather than the transaction's snapshot.
   *
   * <p>Returns scalars, not entities, deliberately. An entity read is served from the persistence
   * context, which is what defeated the earlier attempts to fix this: a sibling refreshed under its
   * lock was observed reverting to its pre-lock value later in the same request, so the sum still
   * saw stale stock. Scalars are not managed, so nothing can put the old value back.
   *
   * @param sampleId the parent sample
   * @param sampleDeleted the parent's own deleted flag, which decides whether subsamples deleted
   *     along with it still count towards its total (mirrors {@code
   *     SampleEntity.getActiveSubSamples})
   */
  List<QuantityInfo> getActiveQuantitiesForUpdate(Long sampleId, boolean sampleDeleted);

  /**
   * One subsample's quantity, read with a row lock ({@code SELECT ... FOR UPDATE}) so the value is
   * current rather than the transaction's snapshot. Scalars, not the entity, for the same reason as
   * {@link #getActiveQuantitiesForUpdate}: {@code lockRowForUpdate} guarantees serialisation only,
   * and an entity read is served (and re-staled) by the persistence context.
   *
   * @return the current quantity, or null when the subsample has none or does not exist; callers
   *     lock and 404-check the subsample first, so a missing row cannot be confused with a null
   *     quantity
   */
  QuantityInfo getQuantityForUpdate(Long subSampleId);

  /**
   * Whether the subsample is soft-deleted, read with a row lock ({@code SELECT ... FOR UPDATE}) so
   * it is the committed value rather than the transaction's snapshot.
   *
   * <p>A scalar for the same reason as {@link #getQuantityForUpdate}: a caller loads the subsample
   * before taking any lock, and {@code lockRowForUpdate} then hands back that same managed
   * instance, so an {@code isDeleted()} asked of the entity answers from before the wait. A soft
   * delete keeps the row and its quantity, so nothing else on this path notices one that landed in
   * between: the decrement proceeds and the full-row write puts {@code deleted = false} back,
   * resurrecting the record while creating material from it (Codex review, P1).
   *
   * @return the committed flag, or null when no row has that id
   */
  Boolean isDeletedForUpdate(Long subSampleId);

  /**
   * One subsample's user-facing version, read with a row lock ({@code SELECT ... FOR UPDATE}) so it
   * is the committed value rather than the transaction's snapshot. A scalar for the same reason as
   * {@link #getQuantityForUpdate}: two operations can both load the entity at version N before
   * either takes the lock, and {@code lockRowForUpdate} hands the waiter back that same cached
   * instance, so the entity's own version field is stale by the time the lock is granted.
   *
   * @return the committed version, or null when the subsample does not exist; callers lock and
   *     404-check the subsample first
   */
  Long getVersionForUpdate(Long subSampleId);

  /**
   * Reconciles the given entity's parent location with the one committed on its row, read under a
   * lock.
   *
   * <p>{@code parentLocation} is a to-one whose foreign key is a COLUMN on the SubSample row, so
   * the full-row UPDATE that makes a stock decrement land (see {@code SampleDynamicUpdateIT}) also
   * writes it, from the snapshot the caller loaded before taking any lock. A move committed in
   * between drops the vacated {@code ContainerLocation} row, so writing the stale foreign key back
   * fails the constraint and takes the whole decrement down; without the constraint it would put
   * the subsample back in the container it was moved out of.
   *
   * <p>That is not the field-level last-write-wins {@code GenericDao.lockRowForUpdate} documents
   * and DevDocs/adr/0007 accepts. A reverted name is a stale value; a reverted location is a
   * physical record in the wrong place. So the column is reconciled with the locked row, exactly as
   * the quantity and the version are, and for the same reason.
   *
   * <p>PRECONDITION: the row is already locked and the entity is not yet dirty. This runs an HQL
   * query against the SubSample table, and Hibernate's AUTO flush mode would flush pending changes
   * to that table first, writing the row from the snapshot this call exists to correct.
   */
  void refreshParentLocationFromLockedRow(SubSample subSample);
}
