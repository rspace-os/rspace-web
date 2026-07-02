package com.researchspace.service.inventory;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleSearchResult;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleTemplateSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSampleWithoutSubSamples;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SampleTemplate;
import jakarta.ws.rs.NotFoundException;
import java.util.List;

/** Handles API actions around Inventory Sample. */
public interface SampleApiManager extends InventoryApiManager<SampleEntity> {

  /**
   * Get All not-deleted samples that user can see. Optionally limit to samples belonging to
   * particular owner.
   */
  ApiSampleSearchResult getSamplesForUser(
      PaginationCriteria<Sample> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /**
   * Return samples created from a given template, visible to the given user. Optionally limit to
   * containers belonging to a particular owner.
   */
  ApiInventorySearchResult getSamplesCreatedFromTemplate(
      Long templateId,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      PaginationCriteria<Sample> pgCrit,
      User user);

  /**
   * Return all samples created from non-latest version of the template. belonging to the given
   * user.
   */
  List<ApiInventoryRecordInfo> getSamplesLinkingOldTemplateVersion(Long templateId, User user);

  /** Checks if sample with given id exists */
  boolean exists(long id);

  /**
   * Returns the {@link Sample} (not a template) if it exists and user has read permission.
   *
   * @param id
   * @param user
   * @return the loaded sample
   * @throws javax.ws.rs.NotFoundException if no sample (as opposed to a template) has that id
   */
  Sample assertUserCanReadSample(Long id, User user);

  /**
   * Returns the {@link Sample} (not a template) if it exists and user has edit permission.
   *
   * @param id
   * @param user
   * @return the loaded sample
   * @throws javax.ws.rs.NotFoundException if no sample (as opposed to a template) has that id
   */
  Sample assertUserCanEditSample(Long id, User user);

  /**
   * Returns the {@link Sample} (not a template) if it exists and user can delete/restore it.
   *
   * @param id
   * @param user
   * @return the loaded sample
   * @throws javax.ws.rs.NotFoundException if no sample (as opposed to a template) has that id
   */
  Sample assertUserCanDeleteSample(Long id, User user);

  /**
   * Returns the {@link Sample} (not a template) if it exists and user has transfer permission.
   *
   * @param id
   * @param user
   * @return the loaded sample
   * @throws javax.ws.rs.NotFoundException if no sample (as opposed to a template) has that id
   */
  Sample assertUserCanTransferSample(Long id, User user);

  /**
   * Returns the {@link SampleTemplate} if it exists and user has read permission.
   *
   * @param id
   * @param user
   * @return the loaded sample template
   * @throws javax.ws.rs.NotFoundException if no template (as opposed to a sample) has that id
   */
  SampleTemplate assertUserCanReadSampleTemplate(Long id, User user);

  /**
   * Returns the {@link SampleTemplate} if it exists and user has edit permission.
   *
   * @param id
   * @param user
   * @return the loaded sample template
   * @throws javax.ws.rs.NotFoundException if no template (as opposed to a sample) has that id
   */
  SampleTemplate assertUserCanEditSampleTemplate(Long id, User user);

  /**
   * Returns the {@link SampleTemplate} if it exists and user can delete/restore it.
   *
   * @param id
   * @param user
   * @return the loaded sample template
   * @throws javax.ws.rs.NotFoundException if no template (as opposed to a sample) has that id
   */
  SampleTemplate assertUserCanDeleteSampleTemplate(Long id, User user);

  /**
   * Returns the {@link SampleTemplate} if it exists and user has transfer permission.
   *
   * @param id
   * @param user
   * @return the loaded sample template
   * @throws javax.ws.rs.NotFoundException if no template (as opposed to a sample) has that id
   */
  SampleTemplate assertUserCanTransferSampleTemplate(Long id, User user);

  /** Checks if sample with given name exists for the user */
  boolean nameExistsForUser(String sampleName, User user);

  /**
   * Creates the Sample according to provided apiSample definition, including fields, extra fields
   * and subsamples.
   *
   * @returns newly created sample
   */
  ApiSampleWithFullSubSamples createNewApiSample(ApiSampleWithFullSubSamples apiSample, User user);

  /**
   * @returns ApiSample with a given id
   */
  ApiSample getApiSampleById(Long id, User user);

  /**
   * Returns the sample as it was at the given user-facing version, with outgoing fields (permitted
   * actions etc.) populated against the live sample. The current version is served as a regular
   * live retrieval; older versions resolve to a read-only historical snapshot. Returns null if the
   * version does not exist.
   */
  ApiSample getApiSampleVersion(Long sampleId, Long version, User user);

  /**
   * Return subsamples of a sample when searched by the UI.
   *
   * @return Sample with a given id with initialized subsample list
   * @throws NotFoundException if Sample with a given id doesn't exist
   */
  ApiInventorySearchResult searchSubSamplesBySampleId(
      Long sampleId,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      PaginationCriteria<InventoryRecord> pgCrit,
      User user);

  /**
   * @return sample (or sample template) with a given id with uninitialized subsample list
   * @throws NotFoundException if Sample with a given id doesn't exist
   */
  SampleEntity getSampleById(Long id, User user);

  /**
   * @return updated sample
   */
  ApiSample updateApiSample(ApiSampleWithoutSubSamples apiSample, User user);

  /**
   * @return sample with updated owner field
   */
  ApiSample changeApiSampleOwner(ApiSampleInfo apiSample, User user);

  void saveDbSampleUpdate(SampleEntity dbSample, User user);

  /**
   * Marks sample as deleted - it will no longer be included in standard listings. Also marks all
   * its subSamples as deleted, with all the consequences.
   *
   * <p>If subSamples are being stored inside containers, the action will not proceed, unless
   * `forceDelete` is set to true. This is to support UI asking user about confirming deletion in
   * such case.
   *
   * @param sampleId
   * @param forceDelete
   * @param user
   * @return deleted sample
   */
  ApiSample markSampleAsDeleted(Long sampleId, boolean forceDelete, User user);

  /**
   * Un-deletes the sample.
   *
   * @param sampleId
   * @param user
   * @param includeSubSamplesDeletedOnSampleDeletion if set to true also undeletes subsamples that
   *     were active during sample deletion
   * @return restored sample
   */
  ApiSample restoreDeletedSample(
      Long sampleId, User user, boolean includeSubSamplesDeletedOnSampleDeletion);

  /**
   * Makes and saves a full copy of sample, images, storage temperature and Fields and extra fields
   * but <em>not</em> subsamples
   *
   * @param sampleId
   * @param user
   * @return The duplicated sample.
   */
  ApiSampleWithFullSubSamples duplicate(Long sampleId, User user);

  /**
   * Makes and saves a full copy of a sample template.
   *
   * @return duplicated template
   */
  ApiSampleTemplate duplicateTemplate(Long templateId, User user);

  /**
   * Gets all non-deleted Templates visible to the user Optionally limit to templates belonging to
   * particular owner.
   */
  ApiSampleTemplateSearchResult getTemplatesForUser(
      PaginationCriteria<SampleTemplate> pgCrit,
      String ownedBy,
      InventorySearchDeletedOption deletedOption,
      User user);

  /**
   * Gets all non-deleted Templates
   *
   * @param user
   * @return
   */
  List<SampleTemplate> getAllTemplates(User user);

  SampleTemplate getSampleTemplateByIdWithPopulatedFields(Long id, User user);

  ApiSampleTemplate getApiSampleTemplateById(Long id, User user);

  ApiSampleTemplate getApiSampleTemplateVersion(Long id, Long version, User user);

  /**
   * Handler for sampleTemplates POST
   *
   * @param formPost
   * @param user
   * @return
   */
  ApiSampleTemplate createSampleTemplate(ApiSampleTemplatePost formPost, User user);

  /**
   * @returns updated sample template
   */
  ApiSampleTemplate updateApiSampleTemplate(ApiSampleTemplate apiSample, User user);

  /**
   * Tries updating the sample so it matches latest definition of the template it was taken from.
   */
  ApiSample updateSampleToLatestTemplateVersion(Long sampleId, User user);

  SampleEntity saveIconId(SampleEntity sample, Long iconId);
}
