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
}
