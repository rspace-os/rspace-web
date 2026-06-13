package com.researchspace.dao;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.FileProperty;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Sample;
import java.util.List;

/** For DAO operations specific to Inventory {@link Sample} (not templates). */
public interface SampleDao extends SampleEntityDao<Sample> {

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
   * Returns all (non-template) samples that reference the given {@link FileProperty} as their image
   * or thumbnail.
   */
  List<Sample> getAllUsingImage(FileProperty fileProperty);

  /**
   * Returns {@code true} if an entity (sample <em>or</em> template) with the given name already
   * exists for the given owner. This method deliberately counts across both discriminator values to
   * preserve the legacy name-uniqueness behaviour that prevented a user from creating a sample and
   * a template with the same name.
   *
   * @param name the name to check
   * @param owner the owning user
   * @return {@code true} if at least one sample or template with that name is owned by the user
   */
  boolean entityNameExistsForUser(String name, User owner);
}
