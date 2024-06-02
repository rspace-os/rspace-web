package com.researchspace.service.impl;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EditStatus;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.IdConstraint;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.InheritACLNotFromFoldersFromParentsPropagationPolicy;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.DMPManager;
import com.researchspace.service.DeletionExecutor;
import com.researchspace.service.DeletionPlan;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.FolderDeletionOrderPolicy;
import com.researchspace.service.FolderManager;
import com.researchspace.service.NotificationConfig;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.RecordFavoritesManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.UserManager;
import com.researchspace.session.UserSessionTracker;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecordDeletionManagerImpl implements RecordDeletionManager {

  static enum DeletionContext {
    FOLDER,
    DOCUMENT
  }

  private Logger log = LoggerFactory.getLogger(RecordDeletionManagerImpl.class);

  private @Autowired FolderManager folderMgr;
  private @Autowired RecordSharingManager recShareMgr;
  private @Autowired CommunicationManager commMgr;
  private @Autowired RecordGroupSharingDao groupshareRecordDao;
  private @Autowired FolderDao folderDao;
  private @Autowired RecordEditorTracker tracker;
  private @Autowired RecordDao recordDao;
  private @Autowired RecordManager recordMgr;
  private @Autowired IPermissionUtils permissnUtils;
  private @Autowired DeletionExecutor deletionExecutor;
  private @Autowired FolderDeletionOrderPolicy deletionOrderPolicy;
  private @Autowired UserManager userMger;
  private @Autowired RecordFavoritesManager favoritesManager;
  private @Autowired AuditTrailService auditService;
  private @Autowired DMPManager dmpManager;

  @Value
  @Builder
  public static class DeletionSettings {

    private Long grandParentFolderId;
    private boolean notebookEntryDeletion;
    private Folder parent;
    private UserSessionTracker currentUsers;
    @ToString.Exclude // throws lazy initialisation exception for toString on User roles
    BiConsumer<Long, EditStatus> noAccessHandler;
  }

  @Override
  public ServiceOperationResultCollection<CompositeRecordOperationResult, Long> doDeletion(
      Long[] toDelete,
      Supplier<String> idProvider,
      DeletionSettings context,
      User user,
      ProgressMonitor progress)
      throws DocumentAlreadyEditedException {
    CompositeRecordOperationResult recordDeletionResult;
    ServiceOperationResultCollection<CompositeRecordOperationResult, Long> rc =
        new ServiceOperationResultCollection<>();
    Long parentFolderId = context.getParent() != null ? context.getParent().getId() : null;
    for (Long id : toDelete) {
      recordDeletionResult = null;
      boolean isRecord = isRecord(id);
      favoritesManager.deleteFavorites(id, user);
      if (isRecord) {
        log.info("Document is a record");
        EditStatus es =
            recordMgr.requestRecordEdit(id, user, context.getCurrentUsers(), idProvider);
        if (!EditStatus.ACCESS_DENIED.equals(es)
            && !EditStatus.CANNOT_EDIT_OTHER_EDITING.equals(es)) {
          try {
            if (context.isNotebookEntryDeletion()) {
              log.info("deleting notebook entry");
              recordDeletionResult =
                  deleteEntry(context.getGrandParentFolderId(), parentFolderId, id, user);
            } else {
              recordDeletionResult = deleteRecord(parentFolderId, id, user);
            }
          } finally {
            // make sure record is unlocked in case of exception
            recordMgr.unlockRecord(id, user.getUsername(), idProvider);
          }
        } else {
          context.getNoAccessHandler().accept(id, es);
        }
      } else {
        recordDeletionResult = deleteFolder(parentFolderId, id, user);
      }

      if (recordDeletionResult != null) {
        rc.addResult(recordDeletionResult);
        auditService.notify(new GenericEvent(user, recordDeletionResult, AuditAction.DELETE));

      } else {
        log.warn("deletion result was null, deletion event not audited?");
        rc.addFailure(id);
      }
      progress.worked(10);
    }
    return rc;
  }

  @Override
  public CompositeRecordOperationResult<BaseRecord> deleteRecord(
      Long parentFolderId, Long recordId, User deleting)
      throws DocumentAlreadyEditedException, AuthorizationException {
    return deleteRecordOrEntry(null, parentFolderId, recordId, deleting, DeletionContext.DOCUMENT);
  }

  public CompositeRecordOperationResult<EcatMediaFile> deleteMediaFileSet(
      Set<EcatMediaFile> recordIdList, User deleting) {
    CompositeRecordOperationResult<EcatMediaFile> totalResult =
        new CompositeRecordOperationResult<>();
    CompositeRecordOperationResult<EcatMediaFile> currentResult = null;
    String errorMsg;
    for (EcatMediaFile currentMediaFile : recordIdList) {
      errorMsg = "";
      try {
        currentResult =
            deleteEntry(
                null, currentMediaFile.getParent().getId(), currentMediaFile.getId(), deleting);
      } catch (DocumentAlreadyEditedException e) {
        log.error(
            "File [{}] was not been deleted from RSpace: ", currentMediaFile.getFileName(), e);
        errorMsg = e.getMessage();
      }

      try {
        currentMediaFile.setRecordDeleted(currentResult.getSingleResult().isDeleted());
        if (currentMediaFile.isDeleted()) {
          log.info("File [{}] successfully deleted from RSpace", currentMediaFile.getFileName());
          errorMsg = "";
        } else if (StringUtils.isBlank(errorMsg)) {
          errorMsg =
              String.format(
                  "File [%s] was not deleted deleted from RSpace", currentMediaFile.getFileName());
          log.error(
              "File [{}] was not deleted deleted from RSpace", currentMediaFile.getFileName());
        }
        totalResult.addRecord(currentResult.getSingleResult(), errorMsg);
      } catch (InvalidObjectException | NullPointerException ioe) {
        log.error(
            "The operation requested on the file [{}] has returned more than one result: ",
            currentMediaFile,
            ioe);
        totalResult.addRecord(
            currentMediaFile,
            String.format(
                "The operation requested on the file [%s] has returned more than one result: [%s]",
                currentMediaFile, ioe.getMessage()));
      }
    }
    return totalResult;
  }

  @Override
  public CompositeRecordOperationResult deleteEntry(
      Long notebookParentId, Long notebookId, Long entryId, User deleting)
      throws DocumentAlreadyEditedException, AuthorizationException {
    return deleteRecordOrEntry(
        notebookParentId, notebookId, entryId, deleting, DeletionContext.DOCUMENT);
  }

  private CompositeRecordOperationResult deleteRecordOrEntry(
      Long notebookParentId, Long parentId, Long recordId, User deleting, DeletionContext context)
      throws DocumentAlreadyEditedException {

    if (!isRecord(recordId)) {
      return null;
    }
    boolean notebookParentProvided = notebookParentId != null;
    Record toDelete = recordDao.get(recordId);
    assertUserCanDelete(parentId, deleting, toDelete);
    assertDocumentNotCurrentlyEdited(toDelete, deleting);
    Folder sharedFolderRoot = folderDao.getUserSharedFolder(deleting);
    Folder parent = null;
    if (toDelete.isSnippet() && toDelete.isShared()) {
      if (toDelete.getOwnerParent().isPresent()) {
        parent = toDelete.getOwnerParent().get();
      } else {
        parent = toDelete.getSharedFolderParent().get();
      }
    } else {
      parent = getParentFolder(parentId, toDelete);
    }
    Folder parentForPath = notebookParentProvided ? folderDao.get(notebookParentId) : parent;
    RSPath sharedFolderPath = parentForPath.getShortestPathToParent(sharedFolderRoot);

    boolean deleteInSharedFolder = sharedFolderPath.size() > MIN_PATH_LENGTH_TOSHARED_ROOT_FOLDER;
    boolean notebookOwnerDeletesOtherUsersEntry =
        parent.isNotebook()
            && deleting.equals(parent.getOwner())
            && !parent.getOwner().equals(toDelete.getOwner());

    if (deleteInSharedFolder && !parent.isNotebook()) {
      recShareMgr.unshareFromSharedFolder(deleting, toDelete, sharedFolderPath);
    } else if (deleteInSharedFolder || notebookOwnerDeletesOtherUsersEntry) {
      unshareFromNotebook(deleting, parent, toDelete);
    } else {
      doDeleteRecordFromOwnersFolder(deleting, toDelete);
      // this is an actual deletion, notify pi/RSPAC-1339
      doNotification(deleting, context, toDelete);
    }
    deleteDMPs(deleting, toDelete);
    Record rcd = recordDao.save(toDelete);
    return new CompositeRecordOperationResult(rcd, parent);
  }

  private void deleteDMPs(User deleting, Record toDelete) {
    if (toDelete.isEcatDocument()) {
      EcatDocumentFile doc = (EcatDocumentFile) toDelete;
      if (doc.getDocumentType().equals(MediaUtils.DMP_MEDIA_FLDER_NAME)) {
        var dmpUsers = dmpManager.findDMPsByPDF(deleting, doc.getId());
        log.info("deleting {} DMPS - ", dmpUsers.size());
        for (DMPUser dmpu : dmpUsers) {
          dmpManager.remove(dmpu.getId());
        }
      }
    }
  }

  void doNotification(User deleting, DeletionContext context, Record toDelete) {
    // if this is part of a folder deletion, we don't want to notify PI of every doc
    // deleted in the
    // folder, so we suppress notification. If it's deletion of 1 or more
    // individual docs we notify PI
    // RSPAC-1339
    if (DeletionContext.DOCUMENT.equals(context)) {
      NotificationConfig cfg =
          NotificationConfig.documentDeleted(getUsersToNotifyOfDelete(deleting, toDelete));
      if (toDelete.isStructuredDocument()) {
        StructuredDocument sd = toDelete.asStrucDoc();
        if (sd.isTemporaryDoc()) {
          log.warn(
              "RSPAC-1446. Document to be deleted  [{}] is a temp document; not creating a delete"
                  + " notification",
              sd.getId());
          return;
        }
      }
      commMgr.notify(deleting, toDelete, cfg, createDeleteNotificationMsg(deleting, toDelete));
    }
  }

  Set<User> getUsersToNotifyOfDelete(User deleting, BaseRecord toDelete) {
    Set<User> usersToNotify = deleting.getGroupMembersWithViewAll();
    Set<User> toNotifyWithPrefs = new HashSet<>();
    // need to load preferences for users here.
    for (User userToNotify : usersToNotify) {
      User userWithPrefs =
          userMger.getUserAndPreferencesForUser(userToNotify.getUsername()).stream()
              .map(pref -> pref.getUser())
              .findFirst()
              .get();
      toNotifyWithPrefs.add(userWithPrefs);
    }
    return toNotifyWithPrefs;
  }

  private String createDeleteNotificationMsg(User deleting, BaseRecord toDelete) {
    return String.format(
        "%s deleted  '%s' (%s)",
        deleting.getUsername(), toDelete.getName(), toDelete.getGlobalIdentifier());
  }

  private void unshareFromNotebook(User deleting, Folder notebook, Record document) {
    // find the user/group through whom the document was originally shared into
    // notebook, and unshare
    List<RecordGroupSharing> recordSharings =
        groupshareRecordDao.getRecordGroupSharingsForRecord(document.getId());
    for (RecordGroupSharing sharing : recordSharings) {
      if (notebook.equals(sharing.getTargetFolder())) {
        unshareFromUserOrGroup(deleting, document, sharing.getSharee());
        return;
      }
    }
  }

  private void doDeleteRecordFromOwnersFolder(User deleting, Record toDelete) {
    if (deleting.equals(toDelete.getOwner())) {
      toDelete.setRecordDeleted(true);
      unshareFromEveryone(deleting, toDelete);
      for (RecordToFolder r2f : toDelete.getParents()) {
        r2f.markRecordInFolderDeleted(true);
      }
    }
  }

  // owner is deleting, so unshare from all recipients
  private void unshareFromEveryone(User deleting, BaseRecord toDelete) {
    List<AbstractUserOrGroupImpl> grps =
        groupshareRecordDao.getUsersOrGroupsWithRecordAccess(toDelete.getId());
    for (AbstractUserOrGroupImpl userOrGroup : grps) {
      unshareFromUserOrGroup(deleting, toDelete, userOrGroup);
    }
  }

  private void unshareFromUserOrGroup(
      User deleting, BaseRecord toDelete, AbstractUserOrGroupImpl userOrGroup) {
    ConstraintBasedPermission toUpdate =
        permissnUtils.findBy(
            userOrGroup.getPermissions(),
            PermissionDomain.RECORD,
            new IdConstraint(toDelete.getId()));
    ShareConfigElement configEl =
        new ShareConfigElement(
            userOrGroup.getId(), toUpdate.getActions().iterator().next().toString());
    if (userOrGroup.isUser()) {
      configEl.setUserId(userOrGroup.getId());
    }
    recShareMgr.unshareRecord(deleting, toDelete.getId(), new ShareConfigElement[] {configEl});
  }

  private void assertDocumentNotCurrentlyEdited(Record toDelete, User deleting)
      throws DocumentAlreadyEditedException {
    Optional<String> editor = tracker.isEditing(toDelete);
    if (editor.isPresent()
        && !editor.get().equals(deleting.getUsername())
        && toDelete.getOwner().equals(deleting)) {
      throw new DocumentAlreadyEditedException(
          String.format(
              "Possibly the document [%s] is already edited in another "
                  + " browser or tab, or by another user, and cannot be deleted yet.",
              toDelete.getName()));
    }
  }

  protected Folder getParentFolder(Long parentFolderId, BaseRecord toDelete) {
    Folder parent =
        parentFolderId != null
            ? folderDao.get(parentFolderId)
            : toDelete.getParent(); // might still be
    // null
    if (parent == null) {
      throw new IllegalStateException("Cannot identify parent folder");
    }
    return parent;
  }

  // Convenience method to ascertain if we're looking for a record or a folder
  private boolean isRecord(Long id) {
    return recordDao.isRecord(id);
  }

  private void doDeleteFolder(
      Folder parent,
      Folder folderToDelete,
      User userDeleting,
      CompositeRecordOperationResult result) {

    Folder sharedFolderRoot = folderDao.getUserSharedFolder(userDeleting);
    RSPath path = parent.getShortestPathToParent(sharedFolderRoot);
    if (!path.isEmpty() && path.size() > MIN_PATH_LENGTH_TOSHARED_ROOT_FOLDER) {

      // we're in a group folder, so by deleting this folder we are going to actually
      // delete folders
      if (folderToDelete.isNotebook()) {
        recShareMgr.unshareFromSharedFolder(userDeleting, folderToDelete, path);
        folderMgr.removeBaseRecordFromFolder(
            folderToDelete,
            parent.getId(),
            new InheritACLNotFromFoldersFromParentsPropagationPolicy());
        // we don't want to delete items from notebook by 'iterate and delete', so we
        // return here.
        return;
      } else {
        // we're deleting a subfolder from a shared folder.
        DeletionPlan plan =
            deletionOrderPolicy.calculateDeletionOrder(folderToDelete, parent, userDeleting);
        deletionExecutor.execute(result, plan);
        return;
      }
    } else {
      if (folderToDelete.isNotebook()) {
        unshareFromEveryone(userDeleting, folderToDelete);
      }
      // We're deleting the user's own folder, so we mark as deleted for future
      // restore.
      // Folders aren't shared, but notebooks are, so a notebook might have > 1 parent
      // Mark this folder as deleted.
      for (RecordToFolder r2f : folderToDelete.getParents()) {
        r2f.markRecordInFolderDeleted(true);
      }
      folderToDelete.setRecordDeleted(true);
      folderDao.save(folderToDelete);
    }
    iterateAndDelete(folderToDelete, userDeleting, result);
    folderDao.save(folderToDelete);
  }

  private void iterateAndDelete(
      Folder folderToDelete, User userDeleting, CompositeRecordOperationResult result) {
    List<RecordToFolder> toDeleteFrom = new ArrayList<>();
    toDeleteFrom.addAll(folderToDelete.getChildren());
    for (RecordToFolder r2f : toDeleteFrom) {

      if (!r2f.getRecord().isFolder()) {
        try {
          deleteRecordOrEntry(
              null,
              folderToDelete.getId(),
              r2f.getRecord().getId(),
              userDeleting,
              DeletionContext.FOLDER);
          result.addRecord(r2f.getRecord());
        } catch (DocumentAlreadyEditedException | IllegalAddChildOperation e) {
          log.warn("Could not delete document {}, skipping: ", r2f.getRecord().getId(), e);
        }
      } else {
        doDeleteFolder(folderToDelete, (Folder) r2f.getRecord(), userDeleting, result);
      }
    }
  }

  public CompositeRecordOperationResult deleteFolder(
      Long parentFolderId, Long toDeleteId, User deleting) {
    Folder toDelete = folderDao.get(toDeleteId);
    assertUserCanDelete(parentFolderId, deleting, toDelete);

    Folder parent = getParentFolder(parentFolderId, toDelete);
    CompositeRecordOperationResult result = new CompositeRecordOperationResult(toDelete, parent);
    // at this stage we don't know if this is a group folder delete or not, false is
    // default.
    doDeleteFolder(parent, toDelete, deleting, result);
    NotificationConfig cfg =
        NotificationConfig.documentDeleted(getUsersToNotifyOfDelete(deleting, toDelete));
    commMgr.notify(deleting, toDelete, cfg, createDeleteNotificationMsg(deleting, toDelete));
    return result;
  }

  private void assertUserCanDelete(Long parentFolderId, User deleting, BaseRecord toDelete)
      throws AuthorizationException {
    boolean canDelete =
        permissnUtils.isPermitted(toDelete, PermissionType.DELETE, deleting)
            && !toDelete.hasType(RecordType.SHARED_GROUP_FOLDER_ROOT); // RSPAC-1633
    if (!canDelete) {
      throw new AuthorizationException(
          String.format(
              " User %s attempted to delete [%s] from folder [%d]",
              deleting.getFullName(),
              (toDelete == null ? null : toDelete.getId()) + "",
              parentFolderId));
    }
  }
}
