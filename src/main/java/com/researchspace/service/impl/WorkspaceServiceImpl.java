package com.researchspace.service.impl;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.MoveAuditEvent;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
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
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Partial refactoring of WorkspaceController code into the service layer so that it can be used by
 * both legacy and new REST controllers. Only the move operation has been refactored so far to
 * support the share dialog move functionality. Left largely as the original implementation, but
 * added a method to return the ServiceOperationResult of the move operation so there's some
 * feedback when a move fails. For the WorkspaceController, the original implementation of returning
 * a count of the record move successes was kept in place.
 */
@Service
public class WorkspaceServiceImpl implements WorkspaceService {

  private final FolderManager folderManager;
  private final RecordManager recordManager;
  private final BaseRecordManager baseRecordManager;
  private final GroupManager groupManager;
  private final SharingHandler recordShareHandler;
  private final AuditTrailService auditService;
  private final MovePermissionChecker permissionChecker;

  @Autowired
  public WorkspaceServiceImpl(
      FolderManager folderManager,
      RecordManager recordManager,
      BaseRecordManager baseRecordManager,
      GroupManager groupManager,
      SharingHandler recordShareHandler,
      AuditTrailService auditService,
      MovePermissionChecker permissionChecker) {
    this.folderManager = folderManager;
    this.recordManager = recordManager;
    this.baseRecordManager = baseRecordManager;
    this.groupManager = groupManager;
    this.recordShareHandler = recordShareHandler;
    this.auditService = auditService;
    this.permissionChecker = permissionChecker;
  }

  public List<ServiceOperationResult<? extends BaseRecord>> moveRecords(
      List<Long> idsToMove, String targetFolderId, Long sourceFolderId, User user) {
    if (idsToMove == null || idsToMove.isEmpty() || StringUtils.isBlank(targetFolderId)) {
      throw new IllegalArgumentException("Ids to move and target folder are required.");
    }

    Folder usersRootFolder = folderManager.getRootFolderForUser(user);
    Folder target = resolveTargetFolder(targetFolderId, user, usersRootFolder);
    validateMove(idsToMove, sourceFolderId, user, target);

    List<ServiceOperationResult<? extends BaseRecord>> results = new ArrayList<>();
    for (Long toMove : idsToMove) {
      Folder sourceFolder = getMoveSourceFolder(toMove, sourceFolderId, user, usersRootFolder);

      if (isFolder(toMove)) {
        results.add(moveFolder(toMove, sourceFolder, target, user));
      } else {
        results.add(moveDoc(toMove, sourceFolder, target, user));
      }
    }
    return results;
  }

  private void validateMove(List<Long> idsToMove, Long sourceFolderId, User user, Folder target) {
    if (target.getId().equals(sourceFolderId)) {
      throw new IllegalArgumentException(
          "Source and target folder are the same. Id: " + sourceFolderId);
    }

    for (long id : idsToMove) {
      BaseRecord record = baseRecordManager.get(id, user);
      if (!permissionChecker.checkMovePermissions(user, target, record)) {
        throw new AuthorizationException(
            "User: " + user.getId() + " does not have permission to move record with ID: " + id);
      }

      if (id == target.getId()) {
        throw new IllegalArgumentException("Attempt to move record with ID: " + id + " to itself");
      }

      if (record.getParents().stream()
          .anyMatch(p -> p.getFolder().getId().equals(target.getId()))) {
        throw new IllegalArgumentException("Record with ID: " + id + " already in target folder");
      }
    }
  }

  private Folder resolveTargetFolder(String targetFolderId, User user, Folder usersRootFolder) {
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

  private ServiceOperationResult<Folder> moveFolder(
      Long folderId, Folder sourceFolder, Folder target, User user) {
    ServiceOperationResult<Folder> result =
        folderManager.move(folderId, target.getId(), sourceFolder.getId(), user);
    if (result != null && result.isSucceeded()) {
      auditService.notify(new MoveAuditEvent(user, result.getEntity(), sourceFolder, target));
    }
    return result;
  }

  private ServiceOperationResult<? extends BaseRecord> moveDoc(
      Long recordId, Folder sourceFolder, Folder target, User user) {
    BaseRecord toMove = recordManager.get(recordId);

    // moving into a shared notebook, not owned by the user
    if (recordManager.isSharedNotebookWithoutCreatePermission(user, target)) {
      try {
        Group group = groupManager.getGroupFromAnyLevelOfSharedFolder(user, target, null);
        SharingResult sharingResult =
            recordShareHandler.moveIntoSharedNotebook(group, toMove, (Notebook) target);
        return mapShareResultToServiceOperation(sharingResult, toMove);
      } catch (Exception ex) {
        return new ServiceOperationResult<>(null, false, ex.getMessage());
      }
    }

    ServiceOperationResult<? extends BaseRecord> result =
        recordManager.move(recordId, target.getId(), sourceFolder.getId(), user);
    if (result != null && result.isSucceeded()) {
      auditService.notify(new MoveAuditEvent(user, result.getEntity(), sourceFolder, target));
    }
    return result;
  }

  private ServiceOperationResult<BaseRecord> mapShareResultToServiceOperation(
      SharingResult sharingResult, BaseRecord baseRecordToMove) {
    if (sharingResult.getError() != null && sharingResult.getError().hasErrorMessages()) {
      String msg = sharingResult.getError().getAllErrorMessagesAsStringsSeparatedBy(",");
      return new ServiceOperationResult<>(null, false, msg);
    }
    if (sharingResult.getSharedIds() != null
        && sharingResult.getSharedIds().contains(baseRecordToMove.getId())) {
      return new ServiceOperationResult<>(baseRecordToMove, true);
    }
    return new ServiceOperationResult<>(null, false, "Move into shared notebook failed");
  }

  private Folder getMoveSourceFolder(
      Long baseRecordId, Long workspaceParentId, User user, Folder usersRootFolder) {
    /* if workspaceParentId is among parent folders, then use it */
    BaseRecord baseRecord = baseRecordManager.get(baseRecordId, user);
    for (RecordToFolder recToFolder : baseRecord.getParents()) {
      if (recToFolder.getFolder().getId().equals(workspaceParentId)) {
        return recToFolder.getFolder();
      }
    }
    /* workspace parent may be incorrect i.e. for search results. in that case
     * return the parent which would appear in getInfo, or after opening the document */
    RSPath pathToRoot = baseRecord.getShortestPathToParent(usersRootFolder);
    return pathToRoot
        .getImmediateParentOf(baseRecord)
        .orElseThrow(
            () -> new IllegalAddChildOperation("Attempted to get parent folder of root folder"));
  }
}
