package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Sample;
import java.util.List;

/** For DAO operations on Inventory Sample. */
public interface SampleDao extends GenericDao<Sample, Long> {

  /**
   * @return Samples with a given name belonging to the user, or empty Array
   */
  List<Sample> findSamplesByName(String name, User user);

  /**
   * Gets samples visible to the current user. Optionally, limit to samples created from given
   * template, or belonging to particular owner
   *
   * @param pgCrit
   * @param parentTemplateId (optional) limit results to samples created from given template
   * @param ownedBy (optional) limits results to samples belonging to particular owner
   * @param deletedItemsOption (optional) decide about including deleted samples (excluded by
   *     default)
   * @param user
   * @return
   */
  ISearchResults<Sample> getSamplesForUser(
      PaginationCriteria<Sample> pgCrit,
      Long parentTemplateId,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      User user);

  /**
   * Get a global id of a Sample that has a field with given id, or null if there is no such sample.
   */
  GlobalIdentifier getSampleGlobalIdFromFieldId(Long id);

  /**
   * Gets all user samples based on old version of the given template (older than version param
   * provided).
   */
  List<Sample> getSamplesLinkingOlderTemplateVersionForUser(
      Long templateId, Long version, User user);

  /**
   * Use this method over standard 'save' to persist new Sample without getting
   * ConstraintViolationException.
   *
   * @return saved sample
   */
  Sample persistNewSample(Sample sample);

  /**
   * Use this method over standard 'save' if subsamples of the sample were not modified but still
   * should be re-indexed, e.g. after owner change.
   *
   * @return saved sample
   */
  Sample saveAndReindexSubSamples(Sample sample);

  /**
   * Get non-deleted sample templates ({@code template == true}) visible to the current user.
   * Optionally, limit to templates belonging to particular owner
   *
   * @param pgCrit
   * @param ownedBy (optional) limits results to samples belonging to particular owner
   * @param deletedItemsOption
   * @param user
   * @return
   */
  ISearchResults<Sample> getTemplatesForUser(
      PaginationCriteria<Sample> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedItemsOption,
      User user);

  /**
   * @return username of the user who owns the default sample templates.
   */
  String getDefaultTemplatesOwner();

  /**
   * For use with new Templates RSINV-41 based on Samples. <br>
   * Saves the sample, fields plus radio/choice definitions
   *
   * @return saved sample
   */
  Sample persistSampleTemplate(Sample sample);

  Long getTemplateCount();

  int saveIconId(Sample sample, Long iconId);

  /** Should be only called in unit tests (I think). Sets stored default template owner to null. */
  void resetDefaultTemplateOwner();
}
