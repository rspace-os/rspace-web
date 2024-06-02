package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.dtos.RevisionSearchCriteria;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import java.util.Collection;
import java.util.List;

/** Provides database access to Audited documents. */
public interface AuditDao {

  /**
   * Gets deleted documents or document views that a user can restore.
   *
   * @param user
   * @param searchTerm Optional search term, can be <code>null</code>
   * @param pgCrit
   */
  ISearchResults<AuditedRecord> getRestorableDeletedRecords(
      User user, String searchTerm, PaginationCriteria<AuditedRecord> pgCrit);

  /** Updates a folder_AUD tables as record undeleted. */
  int updateDeletedFolderAsRestored(Long folderId);

  /**
   * Gets the revisions for a given document.
   *
   * @param doc The current {@link StructuredDocument}
   * @param pgCrit - can be <code>null</code>. If null, will retrieve <em>ALL</em> revisions
   * @return A {@link List} of {@link AuditedRecord} objects.
   */
  List<AuditedRecord> getRevisionsForDocument(
      StructuredDocument doc, PaginationCriteria<AuditedRecord> pgCrit);

  /** Gets revisions for a given version of a document. */
  List<AuditedEntity<StructuredDocument>> getRevisionsForDocumentVersion(
      Long docId, Number userVersion);

  /** Gets revisions for a given version of a media file. */
  List<AuditedEntity<EcatMediaFile>> getRevisionsForMediaFileVersion(Long mediaId, Number version);

  /**
   * Retrieves a single revision for a specified document, or <code>null</code> if a document of
   * that revision could not be found.
   *
   * @param doc The current {@link StructuredDocument}
   * @param revision The revision number.
   * @return An {@link AuditedRecord} for that revision, or <code>null</code> if it couldn't be
   *     found.
   */
  AuditedRecord getDocumentForRevision(StructuredDocument doc, Number revision);

  AuditedRecord getAttachmentForRevision(long mediaFileId, Number revision);

  Integer getRevisionCountForDocument(StructuredDocument doc, RevisionSearchCriteria srchCriteria);

  <T> List<AuditedEntity<T>> getRevisionsForObject(Class<T> cls, Long primaryKey);

  <T> AuditedEntity<T> getObjectForRevision(Class<T> cls, Long primaryKey, Number revision);

  <T> AuditedEntity<T> getNewestRevisionForEntity(Class<T> clazz, Long objectId);

  List<EcatCommentItem> getCommentItemsForCommentAtDocumentRevision(
      Long commentId, Integer revision);

  /* ============================
   *  test helper methods below
   * ============================ */

  /** Gets all revisions for all Documents of the user */
  List<StructuredDocument> getEveryDocumentAndRevisionModifiedByUser(User user);

  /** Gets a list of records to archive (and possibly remove) */
  Collection<BaseRecord> getRecordsToArchive(int numToKeep);

  /**
   * Deletes old archives for each record
   *
   * @param numToKeep number of revisions of each document to keep
   */
  int deleteOldArchives(int numToKeep);
}
