package com.researchspace.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.dtos.RevisionSearchCriteria;
import com.researchspace.model.record.StructuredDocument;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;

/** Manages revision history of records and documents */
public interface AuditManager {

  /**
   * Gets a list of prior revisions
   *
   * @param current the current document whose revisions we are retrieving
   * @param pgCrit Can be <code>null</code>. If null, <em>all</em> revisions are retrieved.
   * @return A possibly empty but non-null {@link List} of {@link AuditedRecord} objects. <br>
   *     This does not load up fields of the document.
   */
  List<AuditedRecord> getHistory(
      StructuredDocument current, PaginationCriteria<AuditedRecord> pgCrit);

  /**
   * Gets a specific revision or user version of a structured document. Either revision or version
   * parameter must be not-null.
   *
   * @param current the current document whose revisions we are retrieving
   * @param revision The requested revision. Optional - you can provide version instead
   * @param userVersion The requested document version. Optional - you can provide revision instead
   * @return An audited record that wraps the structured document. The document's fields are loaded.
   */
  AuditedRecord getDocumentRevisionOrVersion(
      StructuredDocument current, Number revision, Number userVersion);

  /**
   * Gets a specific version of media file.
   *
   * @param media the media file for which the version should be retrieved
   * @param version the requested media file version
   * @return an audited record that wraps EcatMediaFile
   */
  AuditedRecord getMediaFileVersion(EcatMediaFile media, Number version);

  /** Returns revision number from document id and version number. */
  Number getRevisionNumberForDocumentVersion(Long docId, Number userVersion);

  /** Returns revision number from media file id and version number. */
  Number getRevisionNumberForMediaFileVersion(Long mediaId, Number version);

  AuditedEntity<EcatMediaFile> getRevisionForMediaFile(EcatMediaFile mediaFile, Number revision);

  /**
   * Given a structuredDocument id, and a revision number (this is an Envers revision number stored
   * in an _AUD table), this method will restore the old revision as the current working copy.
   *
   * @param revision an Envers revision
   * @param currentDocId databseId of the current document
   * @return The restored record
   * @throws AuthorizationException if the current document is signed. <b> A signed document cannot
   *     be overwritten </b>.
   */
  StructuredDocument restoreRevisionAsCurrent(Number revision, Long currentDocId);

  /**
   * Gets the number of revisions that match the search criteria.
   *
   * @param currentDocId
   * @param searchCriteria , can be <code>null</code> if no search terms needed.
   * @return the number of revisions of a record.
   */
  Integer getNumRevisionsForDocument(Long currentDocId, RevisionSearchCriteria searchCriteria);

  /**
   * Gets a list of all records deleted by a user, that have the potential to be restored.<br>
   * This can include folders, notebooks and documents. For documents, these are returned without
   * initialisation of field contents.
   *
   * @param user A User
   * @param name Name filter to search for
   * @param pgCrit
   * @return A {@link ISearchResults} of {@link AuditedRecord} objects.
   */
  ISearchResults<AuditedRecord> getDeletedDocuments(
      User user, String name, PaginationCriteria<AuditedRecord> pgCrit);

  /**
   * Loads up a previously deleted record for viewing
   *
   * @param deletedId Teh Id of the deleted record
   * @return An {@link AuditedRecord} with a <code>null</code> revision number field.
   */
  public AuditedRecord restoredDeletedForView(Long deletedId);

  /**
   * Does a full restore of a deleted document or folder
   *
   * @param deletedId
   * @param user
   * @return A RestoreDeletedItemResult
   * @throws AuthorizationException if you don't have read permission on a document you're trying to
   *     restore
   */
  public RestoreDeletedItemResult fullRestore(Long deletedId, User user);

  /**
   * Generic method to retrieve revisions of an instance of any audited entity.
   *
   * @param cls
   * @param primaryKey
   * @return A List of AuditedEntities of type T.
   */
  public <T> List<AuditedEntity<T>> getRevisionsForEntity(Class<T> cls, Long primaryKey);

  /**
   * Gets the audited object of specified class with specified identifier at the given revision
   * number.
   *
   * @param cls The entity's class.
   * @param primaryKey
   * @param revision
   * @return An {@link AuditedEntity} that wraps the entity and revision information.
   */
  <T> AuditedEntity<T> getObjectForRevision(Class<T> cls, Long primaryKey, Number revision);

  /**
   * GEts a list of comment items for the cmment wit hthe given id at the document revision
   *
   * @param commentId The id of the EcatComment that contains the required comment items
   * @param revision A revision number of the record holding the comments.
   * @return
   */
  List<EcatCommentItem> getCommentItemsForCommentAtDocumentRevision(
      Long commentId, Integer revision);

  /**
   * Gets the newest revision of the object of specified class with the given database identifier.
   *
   * @param clazz
   * @param objectId
   * @return An {@link AuditedEntity} wrapping the object.
   */
  public <T> AuditedEntity<T> getNewestRevisionForEntity(Class<T> clazz, Long objectId);

  /**
   * Special case of revision retrieval for linked items, that loads up relations as well for
   * permission checking.
   *
   * @param cls
   * @param primaryKey
   * @param revision
   * @return
   */
  <T extends IFieldLinkableElement> AuditedEntity<T> getLinkableElementForRevision(
      Class<T> cls, Long primaryKey, Number revision);
}
