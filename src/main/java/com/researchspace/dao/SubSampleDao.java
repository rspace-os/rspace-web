package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;
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
   * Reads a subsample and holds a row lock on it ({@code SELECT ... FOR UPDATE}) until the current
   * transaction ends, so a concurrent transaction reading the same row this way waits for this one
   * to commit. Used by the operation endpoint so two operations on one origin serialise instead of
   * both decrementing from the same stale quantity (code review, finding 1).
   *
   * @return the locked subsample, or null if none has the id
   */
  SubSample getForUpdate(Long id);
}
