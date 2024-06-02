package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.dtos.RecordTagData;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.views.MessagedServiceOperationResult;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Get/manage tags */
public interface DocumentTagManager {
  static final String SMALL_DATASET_IN_SINGLE_BLOCK =
      "=========SMALL_DATASET_IN_SINGLE_BLOCK=========";
  static final String FINAL_DATA = "=========FINAL_DATA=========";
  // Empirical observations - 1) 120000 results performed with no noticeable lag on a standard AWS
  // instance
  // 2) The largest ontology file on BioOntologies has ~ 110000 terms
  // No actual performance analysis has been done beyond these observations
  static final int MAX_ONTOLOGY_RESULTS_SIZE = 150000;
  static final String TOO_MANY_ONTOLOGY_RESULTS = "=========TOO_MANY_ONTOLOGY_RESULTS=========";
  static final int ONTOLOGY_RESULTS_PAGE_SIZE = 1000;

  Set<String> getTagMetaDataForRecordIds(List<Long> ids, User user);

  List<RecordTagData> getRecordTagsForRecordIds(List<Long> ids, User user);

  /**
   * Save tag values string for given record id.
   *
   * @param recordId id of a document, notebook or a folder
   */
  MessagedServiceOperationResult<BaseRecord> saveTag(Long recordId, String tagtext, User user);

  /**
   * Save tags into set of records
   *
   * @param recordTagsList list of record ids and requested tags
   * @param user user attempting to update the tags
   * @return
   */
  boolean saveTagsForRecords(List<RecordTagData> recordTagsList, User user);

  /**
   * Save tag values string incoming with document PUT request to ELN API
   *
   * @param strucDocId id of a document
   */
  MessagedServiceOperationResult<BaseRecord> apiSaveTagForDocument(
      Long strucDocId, String tagtext, User user);

  void updateUserOntologyDocument(User user);

  /**
   * Get a set of tags metadata used by a user, optionally filtered by a filter or part filter.
   *
   * @param subject the current subject
   * @param tagFilter can be <code>null</code>
   * @return A possibly empty but non-<code>null</code> set of String tags.
   */
  TreeSet<String> getTagsForViewableDocuments(User subject, String tagFilter);

  TreeSet<String> getTagsPlusMetaForViewableDocuments(User subject, String tagFilter);

  TreeSet<String> getTagsPlusMetaForViewableELNDocuments(User subject, String tagFilter);

  TreeSet<String> getTagsPlusMetaForViewableInventoryDocuments(User subject, String tagFilter);

  /**
   * Get a set of tags and ontologies accesible to a user, optionally filtered by a filter or part
   * filter. If ontologies are being enforced, only ontology values will be returned and existing
   * tags will be ignored.
   *
   * @param subject the current subject
   * @param tagFilter can be <code>null</code>
   * @param pos Page number, where page size is ONTOLOGY_RESULTS_PAGE_SIZE
   * @return A possibly empty but non-<code>null</code> set of String terms to be used for tags.
   *     Will have at most ONTOLOGY_RESULTS_PAGE_SIZE elements.
   */
  TreeSet<String> getTagsPlusOntologiesForViewableDocuments(
      User subject, String tagFilter, int pos);

  int getMinUpdateIntervalMillis();

  /**
   * Batch edit from Inventory could attempt to write to Ontology Doc with the same data for each
   * record in the batch: this constants prevents updates to Ontology Doc for a given user occurring
   * with less than MIN_UPDATE_INTERVAL_MILLIS.
   *
   * <p>Set this to a suitable value if multiple tests expect data to be written to the DB and
   * assert the data afterwards.
   *
   * @param minUpdateIntervalMillis
   */
  void setMinUpdateIntervalMillis(int minUpdateIntervalMillis);
}
