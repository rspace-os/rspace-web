package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleTemplate;
import java.util.List;

/** For DAO operations specific to Inventory {@link SampleTemplate}. */
public interface SampleTemplateDao extends SampleEntityDao<SampleTemplate> {

  /**
   * Get non-deleted sample templates visible to the current user. Optionally, limit to templates
   * belonging to particular owner.
   *
   * @param pgCrit
   * @param ownedBy (optional) limits results to samples belonging to particular owner
   * @param deletedItemsOption
   * @param user
   * @return
   */
  ISearchResults<SampleTemplate> getTemplatesForUser(
      PaginationCriteria<SampleTemplate> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      User user);

  /**
   * @return username of the user who owns the default sample templates.
   */
  String getDefaultTemplatesOwner();

  /**
   * For use with new Templates RSINV-41 based on Samples. <br>
   * Saves the template, fields plus radio/choice definitions.
   *
   * @return saved template
   */
  SampleTemplate persistSampleTemplate(SampleTemplate template);

  Long getTemplateCount();

  List<SampleTemplate> getAllTemplatesUsingImage(FileProperty fileProperty);

  /** Sets stored default template owner cache to null. Should only be called in tests. */
  void resetDefaultTemplateOwner();
}
