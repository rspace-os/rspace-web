package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.model.views.RecordTypeFilter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface RecordDao extends GenericDao<Record, Long> {

  /**
   * Gets paginated child records when the pagination is configured to show more results or for
   * orderBy.
   *
   * @param parentId - The Id of the record whose child records we need to retrieve.
   * @param pgCrit Pagination Criteria
   * @return An {@link ISearchResults} object.
   */
  ISearchResults<BaseRecord> getPaginatedChildRecordsOfParentWithFilter(
      Long parentId,
      PaginationCriteria<? extends BaseRecord> pgCrit,
      RecordTypeFilter recordTypefilter);

  /**
   * Retrieves a list of ids of all the visible, non-deleted, normal (i.e., not templates)
   * structured documents belonging to a parent folder id. The ids are all ordered by creation date
   * ascending, i.e., oldest entries first.
   */
  List<Long> getNotebookContentsExcludeFolders(Long parentId);

  /**
   * Gets total number of records in the database owned by the specified users
   *
   * @param users A collection of users to order
   * @param pgCrit PAgination criteria for sorting purposes
   */
  Map<String, DatabaseUsageByUserGroupByResult> getTotalRecordsForUsers(
      Collection<User> users, PaginationCriteria<User> pgCrit);

  /**
   * Gets a record count organised per user, paginated according to pgCrit.
   *
   * @param pgCrit A non-null {@link PaginationCriteria}
   * @return A Map of grouped results. This map will preserve the database's sort order.
   */
  Map<String, DatabaseUsageByUserGroupByResult> getTotalRecordsForUsers(
      PaginationCriteria<User> pgCrit);

  /** Gets record in the database related with field */
  BaseRecord getRecordFromFieldId(long fieldId);

  Long getCountOfUsersWithRecords();

  List<BaseRecord> loadRecordFromResults(List<Object> results);

  /**
   * Gets total record count within a specified folder, excluding deleted items
   *
   * @param parentId The folder/notebook id whose children we will count
   * @param recordFilter An optional RecordTypeFilter to further restrict the types of records
   *     included in the count.
   * @return The number of records in the folder.
   */
  long getChildRecordCount(final Long parentId, RecordTypeFilter recordFilter);

  /**
   * Given a set of recordIds, loads RecordInformation view object in 1 query. <br>
   * This just gets id and name into RecordInformation but other properties are added
   *
   * @param dbids A set of dbids
   * @return Records matching the id
   */
  List<RSpaceDocView> getRecordViewsById(Set<Long> dbids);

  /** Get records by id, from a list of Ids. */
  List<Record> getRecordsById(List<Long> dbids);

  /**
   * Gets all viewable records belonging to the set of users. This method does <em>not</em> return
   * items belonging to other users.
   */
  Set<BaseRecord> getViewableRecordsForUsers(Set<Long> userIds);

  /**
   * Gets all viewable templates belonging to the set of users. This method does <em>not</em> return
   * items belonging to other users.
   */
  Set<BaseRecord> getViewableTemplatesForUsers(Set<Long> userIds);

  /**
   * Gets all viewable media files belonging to the set of users. This method does <em>not</em>
   * return items belonging to other users.
   */
  Set<BaseRecord> getViewableMediaFiles(Set<Long> userIds);

  /** Tests if BaseRecord with id is a Record or not */
  boolean isRecord(Long id);

  List<String> getTagsMetaDataForRecordsVisibleByUserOrPi(User userOrPi, String tagSearch);

  List<String> getTextDataFromOntologiesOwnedByUser(User userOrPi);

  List<String> getTextDataFromOntologyFilesOwnedByUserIfSharedWithAGroup(
      User userOrPi, Long[] ontologyRecordIDs);

  List<BaseRecord> getOntologyFilesOwnedByUser(User userOrPi);

  List<BaseRecord> getOntologyTagsFilesForUserCalled(User userOrPi, String fileName);

  /**
   * If <code>admin</code> is linked to a community, gets all tags in documents created by users of
   * that community. Otherwise, returns an empty set.
   *
   * @return A list of docTag values (these may contain multiple individual tags)
   */
  List<String> getTagsMetaDataForRecordsVisibleByCommunityAdmin(User admin, String tagSearch);

  List<String> getTagsMetaDataForRecordsVisibleBySystemAdmin(User subject, String tagFilter);

  /**
   * Gets some metadata of Documents that link to a media file
   *
   * @return a possibly empty but non-null List
   */
  List<RecordInformation> getInfosOfDocumentsLinkedToMediaFile(Long mediaFileId);

  /**
   * This updates a folderId with a newfolder Id - i.e, changing the parent of a record It is used
   * as hack to avoid strange behaviour when moving multiple records from 1 folder to another (e.g.
   * RSPAC-1735) in the same transaction, whereby hibernate occasionally does not issue an SQL
   * 'delete from RecordToFolder' from old folder <em>In general use RecordManager#move to move
   * items around </em>
   */
  int updateRecordToFolder(RecordToFolder toUpdate, Long newFolderId);

  /** Get the ids of not-deleted notebooks belonging to the user */
  List<Long> getAllNotebookIdsOwnedByUser(User user);

  /**
   * Gets ids of all not-deleted StructuredDocuments that are in notebooks, where both the documents
   * and notebooks are owned by the user
   */
  List<Long> getAllDocumentIdsInNotebooksForUser(User user);

  /** Gets the ids of all not-deleted Structured Documents owned by the user. */
  List<Long> getAllNonTemplateNonTemporaryStrucDocIdsOwnedByUser(User user);

  /**
   * Gets not-deleted document count for a user, based on filter.
   *
   * @return the number of documents owned by the user, of the specified types
   */
  Long getRecordCountForUser(RecordTypeFilter recordTypes, User user);

  List<StructuredDocument> getontologyDocumentsCreatedInPastThirtyMinutesByCurrentUser(
      String uNAme);
}
