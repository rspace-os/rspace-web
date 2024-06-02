package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;

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
}
