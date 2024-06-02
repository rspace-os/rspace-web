package com.researchspace.service;

import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.impl.RecordDeletionManagerImpl.DeletionSettings;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.shiro.authz.AuthorizationException;

/** Performs the delete operations on the object model and persists to the database. */
public interface RecordDeletionManager {

  public static final int MIN_PATH_LENGTH_TOSHARED_ROOT_FOLDER = 2;

  /**
   * Performs the delete operations on the object model and persists to the database. This operation
   * expects the argument record to belong to a parent container. <br>
   * The client should check that the 'deleting' user can acquire an edit lock on this document
   * before calling this method.
   *
   * @param parentFolderId id of the folder the record is deleted from. This can be <code>null
   *     </code>
   * @param recordId id of the record to be deleted
   * @return The RecordDeletionResult
   * @throws AuthorizationException if user is not permitted to delete this record
   * @throws DocumentAlreadyEditedException if document already edited.
   */
  CompositeRecordOperationResult<BaseRecord> deleteRecord(
      Long parentFolderId, Long recordId, User deleting)
      throws DocumentAlreadyEditedException, AuthorizationException;

  /**
   * Performs the delete operations on a list of object model and persists to the database. This
   * operation expects the argument record to belong to a parent container. <br>
   * The client should check that the 'deleting' user can acquire an edit lock on this document
   * before calling this method.
   *
   * @param recordList list of media to be deleted
   * @param deleting the user performing the operation
   * @return The ApiExternaleStorageResult having the list of the IDs to be deleted and the final
   *     status of the operation (included the reason if failed)
   */
  CompositeRecordOperationResult<EcatMediaFile> deleteMediaFileSet(
      Set<EcatMediaFile> recordList, User deleting);

  /**
   * Deletes a notebook entry
   *
   * @param notebookParentId The folder the notebook is contained in. This is used to detect if the
   *     notebook is being deleted from a shared folder ( in which case the notebook is unshared) or
   *     from the owners home folder ( in which case notebook is deleted and unshared)
   * @param notebookId The ID of the notebook the entry belongs to
   * @param entryId The entry to be deleted
   * @param deleting The subject
   * @return
   * @throws DocumentAlreadyEditedException
   * @throws AuthorizationException
   */
  CompositeRecordOperationResult deleteEntry(
      Long notebookParentId, Long notebookId, Long entryId, User deleting)
      throws DocumentAlreadyEditedException, AuthorizationException;

  /**
   * @param parentFolderId
   * @param toDelete
   * @param deleting
   * @throws Exception
   * @see deleteRecord
   */
  CompositeRecordOperationResult deleteFolder(Long parentFolderId, Long toDelete, User deleting);

  /**
   * Top level facade method to handle deletion of one or more REcords or Folders/Notebooks
   *
   * @param toDelete
   * @param an id provider for tracker. Non-API code should supply
   *     SessionAttributeUtils::getSessionId
   * @param context
   * @param user
   * @param progress
   * @return The Parent folder of the items deleted
   * @throws DocumentAlreadyEditedException
   */
  ServiceOperationResultCollection<CompositeRecordOperationResult, Long> doDeletion(
      Long[] toDelete,
      Supplier<String> idProvider,
      DeletionSettings context,
      User user,
      ProgressMonitor progress)
      throws DocumentAlreadyEditedException;
}
