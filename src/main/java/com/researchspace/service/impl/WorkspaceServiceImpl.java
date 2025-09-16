package com.researchspace.service.impl;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.MoveAuditEvent;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.WorkspaceService;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Partial refactoring of WorkspaceController code into the service layer so that it can be used by
 * both legacy and new REST controllers. Only the move operation has been refactored so far to
 * support the share dialog move functionality.
 */
@Service
@Transactional
public class WorkspaceServiceImpl implements WorkspaceService {

  private final FolderManager folderManager;
  private final RecordManager recordManager;
  private final BaseRecordManager baseRecordManager;
  private final GroupManager groupManager;
  private final SharingHandler recordShareHandler;
  private final AuditTrailService auditService;

  @Autowired
  public WorkspaceServiceImpl(
      FolderManager folderManager,
      RecordManager recordManager,
      BaseRecordManager baseRecordManager,
      GroupManager groupManager,
      SharingHandler recordShareHandler,
      AuditTrailService auditService) {
    this.folderManager = folderManager;
    this.recordManager = recordManager;
    this.baseRecordManager = baseRecordManager;
    this.groupManager = groupManager;
    this.recordShareHandler = recordShareHandler;
    this.auditService = auditService;
  }

  /**
   * Left original implementation which returns a count of the success. In the case of the REST api
   * method, we only move 1 at a time, so can assert success based on that. Once other workspace
   * methods are moved to this service layer, this method should be looked at again and updated to
   * return something more meaningful in line with the rest of the class.
   */
  @Override
  public int moveRecords(Long[] idsToMove, String targetFolderId, Long sourceFolderId, User user) {
    if (idsToMove == null || idsToMove.length == 0 || StringUtils.isBlank(targetFolderId)) {
      throw new IllegalArgumentException("No records to move");
    }

    Folder usersRootFolder = folderManager.getRootFolderForUser(user);
    Folder target = resolveTargetFolder(targetFolderId, user, usersRootFolder);

    int moveCounter = 0;
    for (Long toMove : idsToMove) {
      // Do not attempt to move a folder into itself
      if (Objects.equals(target.getId(), toMove)) {
        continue;
      }

      Folder sourceFolder = getMoveSourceFolder(toMove, sourceFolderId, user, usersRootFolder);

      if (isFolder(toMove)) {
        moveCounter += moveFolder(toMove, sourceFolder, target, user);
      } else {
        moveCounter += moveAndMaybeShare(toMove, sourceFolder, target, user);
      }
    }
    return moveCounter;
  }

  private Folder resolveTargetFolder(String targetFolderId, User user, Folder usersRootFolder) {
    // handle input which might contain a '/'
    if ("/".equals(targetFolderId)) {
      return usersRootFolder;
    }
    if (targetFolderId.endsWith("/")) {
      targetFolderId = targetFolderId.substring(0, targetFolderId.length() - 1);
    }
    return folderManager.getFolder(Long.parseLong(targetFolderId), user);
  }

  private boolean isFolder(Long recordId) {
    return !recordManager.exists(recordId) || recordManager.get(recordId).isFolder();
  }

  private int moveFolder(Long folderId, Folder sourceFolder, Folder target, User user) {
    ServiceOperationResult<? extends BaseRecord> result =
        folderManager.move(folderId, target.getId(), sourceFolder.getId(), user);
    return auditIfSucceeded(result, user, sourceFolder, target);
  }

  private int moveAndMaybeShare(Long recordId, Folder sourceFolder, Folder target, User user) {
    BaseRecord baseRecordToMove = recordManager.get(recordId);

    if (recordManager.isSharedNotebookWithoutCreatePermission(user, target)) {
      try {
        Group group = groupManager.getGroupFromAnyLevelOfSharedFolder(user, sourceFolder);
        SharingResult sharingResult =
            recordShareHandler.moveIntoSharedNotebook(group, baseRecordToMove, (Notebook) target);
        // If sharing took place, count the number of created shares; no auditing here as before
        return sharingResult.getSharedIds().size();
      } catch (Exception ex) {
        // Skip this item on error to allow processing of others
        return 0;
      }
    }

    ServiceOperationResult<? extends BaseRecord> result =
        recordManager.move(recordId, target.getId(), sourceFolder.getId(), user);
    return auditIfSucceeded(result, user, sourceFolder, target);
  }

  private int auditIfSucceeded(
      ServiceOperationResult<? extends BaseRecord> moveResult,
      User user,
      Folder sourceFolder,
      Folder target) {
    if (moveResult != null && moveResult.isSucceeded()) {
      auditService.notify(new MoveAuditEvent(user, moveResult.getEntity(), sourceFolder, target));
      return 1;
    }
    return 0;
  }

  private Folder getMoveSourceFolder(
      Long baseRecordId, Long workspaceParentId, User user, Folder usersRootFolder) {
    /* if sourceFolderId is among parent folders, then use it */
    BaseRecord baseRecord = baseRecordManager.get(baseRecordId, user);
    if (workspaceParentId != null) {
      for (RecordToFolder recToFolder : baseRecord.getParents()) {
        if (recToFolder.getFolder().getId().equals(workspaceParentId)) {
          return recToFolder.getFolder();
        }
      }
    }
    /* workspace parent may be incorrect or null. In that case, return the actual parent */
    RSPath pathToRoot = baseRecord.getShortestPathToParent(usersRootFolder);
    return pathToRoot
        .getImmediateParentOf(baseRecord)
        .orElseThrow(
            () -> new IllegalStateException("Attempted to get parent folder of root folder"));
  }
}
