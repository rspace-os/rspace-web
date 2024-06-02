package com.researchspace.service;

import com.researchspace.core.util.CollectionFilter;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.SearchDepth;
import com.researchspace.model.User;
import com.researchspace.model.record.*;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.TreeViewItem;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;

public interface FolderManager {
  /**
   * Calls to any method which could create an API_INBOX, should be inside a synchronized block
   * which locks on this object.
   */
  static final Object API_INBOX_LOCK = new Object();

  /**
   * @param folderid
   * @param user
   * @param depth whether folders should be loaded recursively or not to initialise all subfolder
   *     trees for use outside session. ZERO = this folder, 1 = load children; infinite = recurse
   * @return
   */
  Folder getFolder(Long folderid, User user, SearchDepth depth);

  /**
   * Equivalent to getFolder(folderID, user, false). <br>
   * Throws ObjectRetrievalFailureException Runtime Exception is thrown if nothing is found, or
   * AuthorizationException if user can't read the folder.
   *
   * @param folderid
   * @param user
   * @return A {@link Folder} or exception
   */
  Folder getFolder(Long folderid, User user);

  /**
   * A notebook is a subclass of a folder but for convenience this method returns a notebook. This
   * method also calculates the number of entries and sets that into the returned notebook.
   *
   * @param folderid
   * @return the Notebook.
   */
  Notebook getNotebook(Long folderid);

  Folder save(Folder f, User user);

  /**
   * MArks a record as deleted
   *
   * @param toDeleteID
   * @param folderid
   * @param user
   * @return
   */
  Folder setRecordFromFolderDeleted(Long toDeleteID, Long folderid, User user);

  /**
   * Actually removes a record from the collection of records in this folder.
   *
   * @param toDeleteID - a RECORD id, not a folder id
   * @param folderid
   * @param user
   * @return
   */
  Folder removeRecordFromFolder(Long toDeleteID, Long folderid, User user);

  /**
   * Actually removes a BaseRecord from the collection of records in this folder.
   *
   * @param toDelete A BaseRecord
   * @param parentfolderid The id of the parent folder from which toDelete will be removed.
   * @return The parent folder
   */
  Folder removeBaseRecordFromFolder(BaseRecord toDelete, Long parentfolderid);

  /**
   * @param toDelete A BaseRecord
   * @param parentfolderid The id of the parent folder from which toDelete will be removed.
   * @param aclPolicy an {@link ACLPropagationPolicy} to set ACL behaviour during deletion
   * @see removeBaseRecordFromFolder (BaseRecord toDelete, Long parentfolderid)
   */
  Folder removeBaseRecordFromFolder(
      BaseRecord toDelete, Long parentfolderid, ACLPropagationPolicy aclPolicy);

  /**
   * Creates and persists a new empty Folder
   *
   * @param parentId The Id of the parent container record
   * @param user
   * @param folderName A non-emptyFolder Name
   * @return The newly created Folder, or <code>null</code> if creation was not permitted.
   */
  Folder createNewFolder(long parentId, String folderName, User user);

  /**
   * For creating from XML exports
   *
   * @param id
   * @param name
   * @param owner
   * @param override
   * @return
   */
  Folder createNewFolder(Long id, String name, User owner, ImportOverride override);

  /**
   * Creates and persists a new empty notebook
   *
   * @param parentId The Id of the parent container record
   * @param notebookName A non-empty Name
   * @param context a RecordContext
   * @param u
   * @return The newly created Notebook.
   */
  Notebook createNewNotebook(long parentId, String notebookName, RecordContext context, User u);

  /**
   * For creating from XML exports
   *
   * @param parentId
   * @param notebookName
   * @param context
   * @param subject
   * @param override
   * @return The newly created Notebook.
   */
  Notebook createNewNotebook(
      long parentId,
      String notebookName,
      RecordContext context,
      User subject,
      ImportOverride override);

  /**
   * Makes a recursive copy of the folder and its subfolders
   *
   * @param toCopyFolderid
   * @param user
   * @param newName A new name, not empty
   * @return
   */
  RecordCopyResult copy(Long toCopyFolderid, User u, String newName);

  /**
   * @param toMoveId the Id of the folder to be moved.
   * @param targetFolderId The target destination folder Id.
   * @param sourceFolderId The original parent to be removed
   * @param user
   * @return <code>true</code> if move succeeded, false otherwise
   * @throws IllegalAddChildOperation
   */
  ServiceOperationResult<Folder> move(
      Long toMoveId, Long targetFolderId, Long sourceFolderId, User user)
      throws IllegalAddChildOperation;

  List<Long> getRecordIds(Folder fd);

  /**
   * A transactional method that adds and persists a child (Record or Folder) to the folder
   * identified by folderID. The child record is also persisted as well, and all hibernate
   * relationships are maintained.
   *
   * @param folderId The id of the parent folder
   * @param child The {@link BaseRecord} to add as a child. This can be a transient object.
   * @param owner The owner of the relationship between the
   * @return The parent folder
   * @throws IllegalAddChildOperation
   */
  Folder addChild(Long folderId, BaseRecord child, User owner);

  /**
   * Alternative to <code>addChild(Long folderId, BaseRecord child, User owner)</code> where the
   * {@link ACLPropagationPolicy#DEFAULT_POLICY} is not the appropriate one.
   *
   * @throws IllegalAddChildOperation
   */
  Folder addChild(Long folderId, BaseRecord child, User owner, ACLPropagationPolicy aclPolicy);

  /**
   * A variant of addChild where client can optionally suppress any IllegalAddChild exception. The
   * use case for invoking this is where adding a child is part of a complex transaction. If the
   * exception is thrown out of a transactional method, the whole transaction rolls back.<br>
   * This might be what we want, but not always, e.g. when adding a new group member we want them to
   * be able to see the Shared Lab folder even if their home folder can't be added to PI's Shared
   * folder to avoid cycles, and throws this exception
   *
   * <p>See RSPAC-1949 for details
   *
   * @param folderId
   * @param child
   * @param owner
   * @param aclPolicy
   * @param suppressIllegalAddChild Boolean, if true, this method won't throw an IACO exception
   * @return ServiceOperationResult<Folder>. If IllegalAddChildOperationException is thrown and
   *     suppressed, then isSucceeded == false.
   * @throws IllegalAddChildOperation if suppressIllegalAddChild == false and adding a child would
   *     result in a cycle.
   */
  ServiceOperationResult<Folder> addChild(
      Long folderId,
      BaseRecord child,
      User owner,
      ACLPropagationPolicy aclPolicy,
      boolean suppressIllegalAddChild);

  Folder getGalleryRootFolderForUser(User user);

  /**
   * Given the ID of a group-shared folder(i.e., a folder where
   *
   * <p><code>hasType(RecordType.SHARED_FOLDER) == true </code>, it will retrieve the root
   * group-shared folder ( or root indivudual shared item folder). <br>
   * If the folder identified by the argument srcRecordId is not a sub folder of the group folder,
   * this method return <code>null</code>.
   *
   * @param srcRecordId
   * @param user The authenticated user
   * @return The shared group folder, or <code>Optional.empty</code> if this couldn't be found.
   */
  Optional<Folder> getGroupOrIndividualShrdFolderRootFromSharedSubfolder(
      Long srcRecordId, User user);

  /**
   * Ease of use method returns the root record always
   *
   * @param subject autheniticated subject
   * @param user The user whose root folder is to be accessed.
   * @return The user's root folder.
   */
  Folder getRootRecordForUser(User subject, User user);

  /** Gets the user's 'home' folder that is at the root of his folder tree. */
  Folder getRootFolderForUser(User user);

  /** Get's the user's lab groups folder that is in their shared folder. */
  Folder getLabGroupsFolderForUser(User user);

  /** Get folder ID of the Users' Lab group folder */
  Long getLabGroupFolderIdForUser(User user);

  /**
   * Gets the a media folder for the specified user given the path
   *
   * @param user
   * @return
   */
  Folder getMediaFolderFromURLPath(String path, User user);

  /**
   * Used by file tree - returns a record based on a path of names. The ROOT Record is "/" and a
   * path to its children would be "/child/child/targetRecord". <br>
   * This method should initialize children collection, filtered by a the collection filter
   *
   * @param id
   * @param user
   * @param filter
   * @return
   */
  Folder getFromURLPath(String path, User user, CollectionFilter<BaseRecord> filter);

  Folder getInitialisedFolder(Long fId, User user, CollectionFilter<BaseRecord> filter);

  /**
   * Gets the template folder for the user
   *
   * @param user
   * @return
   */
  Folder getTemplateFolderForUser(User user);

  /**
   * All use of this method must be inside a synchronized block else duplicate API_INBOX can be
   * created. Synchronized blocks should use the API_INBOX_LOCK object. Synchonizing this method
   * itself will not create thread safety, as this method is a transaction boundary, and the Spring
   * Proxy that opens transactions will allow multiple threads to open transactions before any
   * thread reaches the synchonized method of the underlying class.
   *
   * <p>Locates a folder to which API-generated content should be added:
   *
   * <ul>
   *   <li>If <code>folderId</code> is non-null, the corresponding folder will be returned.
   *   <li>If <code>folderId</code> is <code>null</code>,the default API inbox folder for the
   *       content will be returned. This folder will be created if it does not exist.
   * </ul>
   *
   * @param contentType The GalleryContent type as returned by {@link
   *     MediaUtils#extractFileType(String fileSuffix)} or an empty string if the target folder is
   *     in the Workspace, not the Gallery.
   * @param subject The current user
   * @param folderId Optional, can be <code>null</code>. If set, must be a folder Id that is
   *     consistent with the content type being added
   * @return A {@link Folder}
   * @throws AuthorizationException if a target folder is specified but user does not have
   *     permission to create content
   * @throws IllegalArgumentException if the target folder is not in correct locations for the type
   *     of content being uploaded. E.g if content type is an image then the target folder, if
   *     specified, must be the Gallery Images folder or a subfolder of the Gallery Images folder.
   *     Folders cannot be in the Shared folder hierarchy either.
   */
  Folder getApiUploadTargetFolder(String contentType, User subject, Long targetFolderId);

  /**
   * Locates a folder in which imports from external sources can be paced by default.<br>
   * Creates the folder in the user's home folder if necessary.
   *
   * @param subject
   * @return
   */
  Folder getImportsFolder(User subject);

  /**
   * Gets TreeView view of folder tree
   *
   * @param folderId
   * @param pgCrit
   * @param subject
   * @return an {@link ISearchResults} of {@link TreeViewItem}
   */
  ISearchResults<TreeViewItem> getFolderListingForTreeView(
      Long folderId, PaginationCriteria<TreeViewItem> pgCrit, User subject);

  /**
   * Gets a folder, returning an empty Optional if folder does not exist. If folder exists, this
   * method also asserts read permission on the folder.
   *
   * @param folderid
   * @param user
   * @return An Optional<Folder>
   */
  Optional<Folder> getFolderSafe(Long folderid, User user);

  /**
   * As for getFolder, but does not throw AuthorisationException if folder is deleted if <code>
   * includedDeleted</code> is <code>true</code>.
   *
   * @param folderid
   * @param user
   * @param includeDeleted
   * @return
   */
  Folder getFolder(Long folderid, User user, boolean includeDeleted);

  /**
   * Facade method to create subfolder of a Gallery folder.
   *
   * @param name The name for the new folder
   * @param mediaFolderName The Gallery type (one of the MediaUtils#GALLERY_MEDIA_FOLDERS strings)
   * @param user The subject
   * @return The created subfolder
   * @throws IllegalArgumentException if invalid <code>mediaFolderName</code>
   */
  Folder createGallerySubfolder(String name, String mediaFolderName, User user);
}
