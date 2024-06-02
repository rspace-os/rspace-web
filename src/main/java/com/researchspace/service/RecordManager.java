package com.researchspace.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EditStatus;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dtos.GalleryFilterCriteria;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.DocumentInitializationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.FolderRecordPair;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.session.UserSessionTracker;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.shiro.authz.AuthorizationException;

/**
 * Contains all the record actions required by RSpace. Which includes all CRUD operations, making a
 * template and generating a history
 */
public interface RecordManager {

  /**
   * A low-level operation to get a Record. This method does not take into account authorization or
   * deleted state. ObjectRetrievalFailureException if there is no record for given id
   *
   * @param id
   * @return record
   */
  Record get(long id);

  /**
   * Gets a list of records by id, which are authorised. IF all documents are authorised, the length
   * of the returned list will be the same length as the initial list. See RSPAC-17
   *
   * @param ids A List of Record Ids
   * @param subject authenticated user
   * @param permissionType permissionType
   * @return A potentially empty, but non-null list of Records.
   */
  List<Record> getAuthorisedRecordsById(
      List<Long> ids, User subject, PermissionType permissionType);

  /**
   * Checks whether there is a record with given id.
   *
   * @param id
   * @return true if there is a record with given id
   */
  boolean exists(long id);

  /**
   * Gets the parent folder of this record that belongs to the owner of the record
   *
   * @param documentId
   * @param user
   * @return The parent Folder of this document which has the same owner as the record, or <code>
   *     null</code> if <code>user</code> is not the document's owner.
   */
  Folder getParentFolderOfRecordOwner(Long documentId, User user);

  /**
   * Gets the record specified by the given id casted to the specified
   * subclass. Doesn't check permissions. <br/>
   * Equivalent to <code>getAsSubclass(long id, Class<T> clazz, false);
   * @param id
   * @param clazz
   *            The class to cast to.
   * @return
   */
  <T extends Record> T getAsSubclass(long id, Class<T> clazz);

  /**
   * Gets a record with initialized collections, depending on the {@link
   * DocumentInitializationPolicy}
   *
   * @param recordId
   * @param user
   * @param initializationPolicy
   * @param ignorePermissions whether to ignore permissions in this lookup. Generally this should be
   *     <code>false</code> unless you have a specific reason to <em>not </em> check permissions (
   *     for example, if you need to load this record to examine its properties, to see if you do in
   *     fact have permission to access it).
   * @return An initialized StructuredDocument
   */
  BaseRecord getRecordWithLazyLoadedProperties(
      long recordId,
      User user,
      DocumentInitializationPolicy initializationPolicy,
      boolean ignorePermissions);

  /**
   * Convenient method for retrieving records with initialised fields, does not ignore permissions.
   */
  BaseRecord getRecordWithFields(long recordId, User user);

  /**
   * Gets the modification date of a specific record.
   *
   * @param recordId
   * @param user
   * @return
   */
  Long getModificationDate(long recordId, User user);

  /**
   * Fine-grained methods that saves or updates the given record by delegating to the DAO layer.
   *
   * @param record
   * @param user
   * @return
   */
  Record save(Record record, User user);

  /**
   * Handles auto saved procedure by creating temporary fields and documents
   *
   * @param field
   * @param user
   * @param data
   * @return
   */
  StructuredDocument saveTemporaryDocument(Field field, User user, String data);

  StructuredDocument saveTemporaryDocument(Long fieldId, User user, String data);

  /**
   * Makes an independent, deep copy of a Record, including comments and image annotations, into a
   * given target Record and persists the new copy. Both Argument {@link Record}s should already be
   * persistent before calling this method.
   *
   * @param id The persistenceID of the Record to copy
   * @param user
   * @param newname The new name of the copied record. If <code>null</code> will be the original
   *     name plus the suffix "_copy".
   * @param targetFolderId an optional target folder if the copied record is to be saved to a
   *     different folder from the original. If this argument is null, the copy will be saved in the
   *     original's parent folder by default.
   * @return A RecordCopyResult encapsulating the freshly copied and persisted {@link Record}.
   */
  RecordCopyResult copy(long id, String newname, User user, Long targetFolderId);

  /**
   * Performs a copy of a template, setting the properties of the copy to make it a normal record.
   *
   * @param templateId the ID of the template from which we will create the document
   * @param newname The name of the new document
   * @param user
   * @param targetFolderId A folder in which to put the created document
   * @return A RecordCopyResult in which the 'original' is template and 'copy' is created doc
   */
  RecordCopyResult createFromTemplate(
      long templateId, String newname, User user, Long targetFolderId);

  /**
   * Get parent folder of a specific base record. In case of shared records the workspace folder is
   * returned, rather than shared.
   *
   * @param user
   * @param original
   * @return
   */
  Folder getRecordParentPreferNonShared(User user, BaseRecord original);

  /**
   * Boolean test as to whether the record <code>original</code> can be moved to targetParent
   *
   * @param original
   * @param targetParent
   * @param user
   * @return <code>true</code> if the move is permitted, <code>false</code> otherwise
   */
  boolean canMove(BaseRecord original, Folder targetParent, User user);

  /**
   * Moves the record identified by <code>id</code> from folder<code>currParentId</code> to the
   * folder <code>targetParent</code>
   *
   * @param id The id of the record to move.
   * @param targetParent A folderID to hold the new record
   * @param currParentId The parent folder this record will be moved from; a record may have > 1
   *     parents so this needs to be specified
   * @param user The subject
   * @return ServiceOperationResult<BaseRecord> with succeeded = <code>true</code> if moved, <code>
   *     false</code> otherwise
   * @throws AuthorizationException if not permitted
   */
  ServiceOperationResult<BaseRecord> move(Long id, Long targetParent, Long currParentId, User user);

  /**
   * This method is called when attempting to access a record for viewing or editing. It analyses
   * permissions and the deleted status, and current edit status of a record, before returning an
   * indication of the editable opportunities. A sessionID is used to track sessions with edit
   * permission on the document, <strong> so this method should not be called by any REST API
   * functions</strong>
   *
   * <p>
   *
   * <ul>
   *   <li>A return object of type {@link EditStatus#EDIT_MODE} indicates this user has successfully
   *       acquired an edit lock.
   *   <li>A return object of type {@link EditStatus#ACCESS_DENIED} indicates that the record is
   *       either
   *       <ul>
   *         <li>Deleted ( by owner, or by current subject)
   *         <li>There is no record of that id
   *         <li>The subject has no view/edit permissions for that document.
   *       </ul>
   *   <li>A return type of {@link EditStatus#CANNOT_EDIT_OTHER_EDITING} implies another user is
   *       editing the document, but subject has view permission.
   *   <li>A return type of {@link EditStatus#CANNOT_EDIT_NO_PERMISSION} the user does not have edit
   *       permission, but has view permission.
   *   <li>A return type of {@link EditStatus#CAN_NEVER_EDIT} indicates that there is an inherent
   *       property of the record that makes it uneditable - e.g., that it is an old revision; but
   *       the subject retains view permission.
   * </ul>
   *
   * <em> It is the caller's responsibility to unlock the record after an editing session ends, by
   * calling <code>unlockRecord()</code>, generally within a finally{} block so that it is always
   * called even if an exception is thrown. </em>
   *
   * @param recordId
   * @param user
   * @param activeUsers A Set of active user names
   * @return
   */
  EditStatus requestRecordEdit(Long recordId, User user, UserSessionTracker activeUsers);

  /**
   * Alternative method which takes a custom ID provider, for use by API functions that need to
   * establish an edit lock
   *
   * @param recordId
   * @param user
   * @param activeUsers
   * @param sessionIDProvider
   * @return
   */
  EditStatus requestRecordEdit(
      Long recordId, User user, UserSessionTracker activeUsers, Supplier<String> sessionIDProvider);

  /**
   * Get EditStatus for viewing a document.
   *
   * @param recordId
   * @param user
   * @param activeUsers
   * @return
   */
  EditStatus requestRecordView(Long recordId, User user, UserSessionTracker activeUsers);

  /**
   * Creates and persists a new empty {@link StructuredDocument}, using the {@link
   * DefaultRecordContext} <br>
   * As a side effect, will increment the user's form usage, if the form is in PUBLISHED state.
   *
   * @param parentId The Id of the parent container record. Can be <code>null</code> if record is
   *     not to appear in a user or group's folder.
   * @param formId The Form's database id
   * @param user authenticated user
   * @return The newly created StructuredDocument or <code>null</code> if
   *     <ul>
   *       <li>No form with the given ID was found
   *       <li>The Form was unpublished or an old version
   *     </ul>
   */
  StructuredDocument createNewStructuredDocument(Long parentId, Long formId, User user);

  /**
   * Overloaded createNewStructuredDocument that takes an {@link RecordContext} that can modify how
   * record-related method works.
   *
   * <p>see createNewStructuredDocument(Long parentId, Long formId, User user) ;
   */
  StructuredDocument createNewStructuredDocument(
      Long parentId, Long formId, User user, RecordContext context, ImportOverride override);

  /**
   * Overloaded createNewStructuredDocument that takes record name. If passed name is blank, default
   * name would be used.
   *
   * <p>see createNewStructuredDocument(Long parentId, Long formId, User user, RecordContext
   * context);
   */
  StructuredDocument createNewStructuredDocument(
      Long parentId, Long formID, String name, User user, RecordContext context);

  /**
   * Convenience method to create a new empty {@link StructuredDocument} based on the BasicDocument
   * form. <br>
   * As a side effect, will increment the user's form usage.
   *
   * @param parentId The Id of the parent container record. Can be <code>null</code> if record is
   *     not to appear in a user or group's folder.
   * @param user the authenticated user who is creating the record.
   * @return The newly created StructuredDocument or <code>null</code> if no BasicDocument form was
   *     found to create the document from.
   */
  StructuredDocument createBasicDocument(Long parentId, User user);

  /**
   * Creates a basic document with initial content
   *
   * @param parentId
   * @param name the name of the structured document
   * @param user
   * @param htmlContent
   * @return A {@link StructuredDocument}
   * @since 1.51
   */
  StructuredDocument createBasicDocumentWithContent(
      Long parentId, String name, User user, String htmlContent);

  /**
   * Convenience method to create a new {@link Snippet} based on a selected content from text field
   * of the record
   *
   * @param name snippet name
   * @param content
   * @param user
   * @return
   */
  Snippet createSnippet(String name, String content, User user);

  /**
   * @throws AuthorizationException if user lacks permissions
   */
  String copySnippetIntoField(Long snippetId, Long fieldId, User user);

  /**
   * Copy content that may contain RSpace elements (annotations, chemicals, etc.).
   *
   * <p>That'll create a deep copy of the elements, and returned content will point to the copied
   * elements, not the originals.
   *
   * @param content
   * @param fieldId
   * @param user
   * @return
   * @throws AuthorizationException if user lacks permissions
   */
  String copyRSpaceContentIntoField(String content, Long fieldId, User user);

  /**
   * Saves a <code>Structured Document</code> by updating from temporary fields and deleting
   * temporary autosaved data.<br>
   * Also increments the document's version.
   *
   * @param structuredDocumentId An identifier for a {@link StructuredDocument}
   * @param uname An authenticated username
   * @param unlockRecord to unlock the record after saving or not.
   * @param warningList optional, for minor problems detected during save
   * @return The updated {@link StructuredDocument}'s parent container.]
   * @throws DocumentAlreadyEditedException if the user calling save does not have an edit lock on
   *     the document
   * @throws AuthorizationException if not WRITE permission to save
   */
  FolderRecordPair saveStructuredDocument(
      long structuredDocumentId, String uname, boolean unlockRecord, ErrorList warningList)
      throws DocumentAlreadyEditedException;

  /**
   * Cancel a <code>Structured Document</code> by deleting from temporary fields and deleting
   * temporary autosaved data.<br>
   *
   * @param structuredDocumentId
   * @param uname
   * @return BaseRecord the parent of this document
   * @throws DocumentAlreadyEditedException
   */
  BaseRecord cancelStructuredDocumentAutosavedEdits(long structuredDocumentId, String uname)
      throws DocumentAlreadyEditedException;

  /**
   * Unlocks the edit lock for a given record.
   *
   * @param record
   * @param user
   * @return
   */
  void unlockRecord(Record record, User user);

  /**
   * Retrieves a list of all the child records and their descendants belonging to a parent record
   * id. The ids are all ordered by creation date <em>ascending</em>. This method delegates to
   * method of the same name found in recordDAO
   *
   * @param parentId
   * @return
   */
  List<Long> getDescendantRecordIdsExcludeFolders(Long parentId);

  /**
   * Builds a list of all entries accessible (readable) to the user for a given notebook id. in
   * creation date ascending order.
   *
   * @param user
   * @param notebookId
   * @return
   */
  List<Record> getLoadableNotebookEntries(User user, Long notebookId);

  /**
   * Unlocks the edit lock on a record
   *
   * @param recordId the record to be unlocked for editing
   * @param username The user who is relinquishing the lock.
   */
  void unlockRecord(Long recordId, String username);

  void unlockRecord(Long recordId, String username, Supplier<String> sessionIdProvider);

  /**
   * Creates a template of a document from the document represented by the recordId argument, and
   * adds it to the user's media/templates folder. AuthorizationException if subject lacks 'COPY'
   * permission.
   *
   * @param recordId This must be an id to a STructuredDocument
   * @param fieldIds A list of fieldIds whose Field's contents we want included in the template
   * @param usr The authenticated user
   * @param templateName A name for the template.
   * @return The created template document, or <code>null</code> if could not be created.
   * @throws DocumentAlreadyEditedException
   */
  StructuredDocument createTemplateFromDocument(
      Long recordId, List<Long> fieldIds, User usr, String templateName)
      throws DocumentAlreadyEditedException;

  /**
   * Forces a version update of a document where a modification has been made to an ancillary object
   * such as a comment, sketch etc., which does not alter the the text of a document's field. The
   * main purpose of this method is to ensure a complete revision history of all changes made to a
   * record.
   *
   * @param recordId - The id of the record that owns the comment, sketch etc
   * @param delta - the delta type of the change being notified
   * @param optionalMsg - can be <code>null</code>.
   * @param user - current user
   */
  void forceVersionUpdate(Long recordId, DeltaType delta, String optionalMsg, User user);

  /**
   * Performs regular folder listing, but configurable by a {@link RecordTypeFilter} that can
   * include or exclude {@link RecordType}s from the returned results.
   *
   * @param parentId
   * @param pgCrit pgCrit a non-<code>null</code> {@link PaginationCriteria}
   * @param recordTypefilter it could be null
   * @return An {@link ISearchResults}
   */
  ISearchResults<BaseRecord> listFolderRecords(
      Long parentId,
      PaginationCriteria<? extends BaseRecord> pgCrit,
      RecordTypeFilter recordTypefilter);

  /**
   * Paginate database lookup of {@link Record} objects. This is equivalent to
   *
   * <pre>
   * 	listFolderRecords(Long parentId, PaginationCriteria<? extends BaseRecord> pgCrit, null);
   * </pre>
   *
   * @param parentId
   * @param pgCrit
   * @return An {@link ISearchResults} object.
   */
  ISearchResults<BaseRecord> listFolderRecords(
      Long parentId, PaginationCriteria<? extends BaseRecord> pgCrit);

  /**
   * Performs regular folder listing, but configurable by a {@link RecordTypeFilter} that can
   * include or exclude {@link RecordType}s from the returned results.
   *
   * @param parentId
   * @param pgCrit pgCrit a non-<code>null</code> {@link PaginationCriteria}
   * @param galleryFilter
   * @param recordTypefilter
   * @return An {@link ISearchResults}
   */
  ISearchResults<BaseRecord> getGalleryItems(
      Long parentId,
      PaginationCriteria<BaseRecord> pgCrit,
      GalleryFilterCriteria galleryFilter,
      RecordTypeFilter recordTypefilter,
      User user);

  /**
   * Sets a new name for a record or folder, truncating it if need be to fit in the allowed database
   * field size. IllegalArgumentException if <code>newname</code> is null or empty
   *
   * @param newname
   * @param toRenameId
   * @param user
   * @return true if could be renamed, false if was not (e.g., if new name is same as old name).
   */
  boolean renameRecord(String newname, Long toRenameId, User user);

  /**
   * Retrieves the media folder of the given type for the specified user, or <code>null</code> if
   * not found. If <code>createIfNotExists</code> is <code>true</code> then the folder will be
   * created and returned
   *
   * @param user
   * @param folderName one of #MediaUtils XXX_MEDIA_FLDER_NAME constants
   * @return The media folder, or <code>null</code> if it does not exist and it could not be
   *     created.
   */
  Folder getGallerySubFolderForUser(String folderName, User user);

  /**
   * Unlock all the locked records using RecordEditorTracker.
   *
   * @param sessionId
   */
  void removeFromEditorTracker(String sessionId);

  /**
   * IllegalArgumentException if <code>dbids</code> is null or empty.
   *
   * @param dbids A non-empty set of record Ids
   * @return A view-only list of document info;
   */
  List<RSpaceDocView> getAllFrom(Set<Long> dbids);

  /**
   * General method to perform the intersection between filters on workspace page.
   *
   * @param filters
   * @param paginationCriteria
   * @param user
   * @return
   */
  ISearchResults<BaseRecord> getFilteredRecords(
      WorkspaceFilters filters, PaginationCriteria<BaseRecord> paginationCriteria, User user);

  /**
   * Method will iterate through every filters (shared with me, favorites, recent and all viewable
   * records) and check if it is active. It will retrieve a list of ALL records using the
   * intersection between active filters.
   *
   * @param filters
   * @param user
   * @return List<BaseRecord>
   */
  List<BaseRecord> getFilteredRecordsList(WorkspaceFilters filters, User user);

  /**
   * Returns user's own templates.
   *
   * @param user
   * @return
   */
  Set<BaseRecord> getViewableTemplates(User user);

  /**
   * Retrieves an {@link EcatImage} directly. Loading of the underlying thumbnail/working image is
   * controlled by the <code>loadImageBytes</code> argument.
   *
   * @param id
   * @param loadImageBytes Whether to load the thumbnail/working image bytes or not. <br>
   *     Unless you're going to stream the images bytes this should normally be false.
   * @return The {@link EcatImage}
   */
  EcatImage getEcatImage(Long id, boolean loadImageBytes);

  /**
   * Gets a Record if it exists
   *
   * @param id
   * @return
   */
  Optional<Record> getSafeNull(long id);

  /**
   * Similar to getRecordWithLazyLoadedProperties but squashes any DataAccessExceptions, returning
   * an Optional.empty if record does not exist. This is to support calls from outer transactions so
   * as not to throw UnexpectedRoollbackEXceptions
   *
   * @param id
   * @param user
   * @param initializationPolicy
   * @param ignorePermissions
   * @return
   */
  Optional<Record> getOptRecordWithLazyLoadedProperties(
      long id,
      User user,
      DocumentInitializationPolicy initializationPolicy,
      boolean ignorePermissions);

  /**
   * Copy operation with additional context object providing information as to the context in which
   * the copy is performed.
   *
   * @param originalId
   * @param newname
   * @param user
   * @param targetFolderId
   * @param context
   * @return
   */
  RecordCopyResult copy(
      long originalId, String newname, User user, Long targetFolderId, RecordContext context);

  /**
   * Gets count of not-deleted records, filtered by recordTypes
   *
   * @param recordTypes
   * @param user
   * @return a Long
   */
  Long getRecordCountForUser(RecordTypeFilter recordTypes, User user);

  List<BaseRecord> getOntologyTagsFilesForUserCalled(User user, String userTagsontologyDocument);

  List<StructuredDocument> getontologyDocumentsCreatedInPastThirtyMinutesByCurrentUser(
      String uName);
}
