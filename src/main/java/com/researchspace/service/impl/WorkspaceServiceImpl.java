package com.researchspace.service.impl;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.MoveAuditEvent;
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

@Service
public class WorkspaceServiceImpl implements WorkspaceService {

  @Autowired private FolderManager folderManager;
  @Autowired private RecordManager recordManager;
  @Autowired private BaseRecordManager baseRecordManager;
  @Autowired private GroupManager groupManager;
  @Autowired private SharingHandler recordShareHandler;
  @Autowired private AuditTrailService auditService;

  @Override
  public int moveRecords(Long[] toMove, String targetFolderId, Long workspaceParentId, User user) {
    if (toMove == null || toMove.length == 0 || StringUtils.isBlank(targetFolderId)) {
      throw new IllegalArgumentException("No records to move");
    }

    Folder usersRootFolder = folderManager.getRootFolderForUser(user);
    Folder target;

    // handle input which might contain a /
    if ("/".equals(targetFolderId)) {
      target = usersRootFolder;
    } else {
      if (targetFolderId.endsWith("/")) {
        targetFolderId = targetFolderId.substring(0, targetFolderId.length() - 1);
      }
      target = folderManager.getFolder(Long.parseLong(targetFolderId), user);
    }

    int moveCounter = 0;
    for (Long recordIdToMove : toMove) {
      if (Objects.equals(target.getId(), recordIdToMove)) {
        continue;
      }
      Folder sourceFolder =
          getMoveSourceFolder(recordIdToMove, workspaceParentId, user, usersRootFolder);
      boolean isFolder =
          !recordManager.exists(recordIdToMove) || recordManager.get(recordIdToMove).isFolder();
      ServiceOperationResult<? extends BaseRecord> moveResult = null;
      if (isFolder) {
        moveResult = folderManager.move(recordIdToMove, target.getId(), sourceFolder.getId(), user);
      } else {
        BaseRecord baseRecordToMove = recordManager.get(recordIdToMove);
        if (recordManager.isSharedNotebookWithoutCreatePermission(user, target)) {
          try {
            Group group = groupManager.getGroupFromAnyLevelOfSharedFolder(user, sourceFolder);
            var sharingResult =
                recordShareHandler.moveIntoSharedNotebook(
                    group, baseRecordToMove, (Notebook) target);
            if (!sharingResult.getSharedIds().isEmpty()) {
              moveCounter = moveCounter + sharingResult.getSharedIds().size();
            }
          } catch (Exception ex) {
            // Skip this item on error to allow processing of others
            continue;
          }
        } else {
          moveResult =
              recordManager.move(recordIdToMove, target.getId(), sourceFolder.getId(), user);
        }
      }
      if (moveResult != null && moveResult.isSucceeded()) {
        moveCounter++;
        auditService.notify(new MoveAuditEvent(user, moveResult.getEntity(), sourceFolder, target));
      }
    }
    return moveCounter;
  }

  private Folder getMoveSourceFolder(
      Long baseRecordId, Long workspaceParentId, User user, Folder usersRootFolder) {
    /* if workspaceParentId is among parent folders, then use it */
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
