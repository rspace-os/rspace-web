package com.researchspace.service.impl;

import com.researchspace.dao.FolderDao;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * Needs its own class as checks both src and target. permissUtils just checks a single instance.
 */
public class MovePermissionChecker {

  private static final int DEPTH_FROM_SHAREDROOT_WHERE_SHARINGALLOWED = 2;

  private @Autowired FolderDao folderDao;
  private @Autowired IPermissionUtils permissionUtils;

  // for spring
  public MovePermissionChecker() {
    super();
  }

  MovePermissionChecker(FolderDao fdao, IPermissionUtils utils) {
    this.folderDao = fdao;
    this.permissionUtils = utils;
  }

  public boolean checkMovePermissions(User user, Folder newparent, BaseRecord original) {
    return checkRecordAgainstTarget(new TargetContext(user, newparent), original);
  }

  /**
   * Page-level variant of {@link #checkMovePermissions(User, Folder, BaseRecord)}: returns, per
   * record id, the same answer the single-record check gives, with the checks that only depend on
   * the target folder (target permission, target path within the shared-folder tree) computed once
   * for the whole page.
   */
  public Map<Long, Boolean> checkMovePermissions(
      User user, Folder newparent, Collection<? extends BaseRecord> originals) {
    TargetContext target = new TargetContext(user, newparent);
    Map<Long, Boolean> movableByRecordId = new LinkedHashMap<>();
    for (BaseRecord original : originals) {
      movableByRecordId.put(original.getId(), checkRecordAgainstTarget(target, original));
    }
    return movableByRecordId;
  }

  /**
   * The record-independent part of the move check: the subject, the target folder, and everything
   * derived from them, computed at most once however many records are checked against it.
   */
  private class TargetContext {
    private final User user;
    private final Folder newparent;
    private final boolean canCreateInTarget;
    // if the permissions are OK and we're not in a group folder, then we can move
    private final boolean outsideGroupFolderContext;
    private boolean pathsResolved;
    private Folder userSharedFolder;
    private RSPath newParentPath;
    private Folder groupFolder;

    private TargetContext(User user, Folder newparent) {
      this.user = user;
      this.newparent = newparent;
      canCreateInTarget =
          permissionUtils.isPermitted(newparent, PermissionType.FOLDER_RECEIVE, user);
      outsideGroupFolderContext =
          !newparent.isSystemFolder() || newparent.isRootFolderForUser(user);
    }

    // resolved only when a record passes both permission gates, so a denied check costs no
    // database work; still at most one query however many records share this target
    private void resolvePaths() {
      if (!pathsResolved) {
        pathsResolved = true;
        userSharedFolder = folderDao.getUserSharedFolder(user);
        newParentPath = newparent.getShortestPathToParent(userSharedFolder);
      }
    }

    // resolved only when a record actually reaches the path comparison, so a target path
    // with no group-folder root fails only where it always did
    private Folder groupFolder() {
      if (groupFolder == null) {
        groupFolder =
            (Folder) newParentPath.findFirstByType(RecordType.SHARED_GROUP_FOLDER_ROOT).get();
      }
      return groupFolder;
    }
  }

  private boolean checkRecordAgainstTarget(TargetContext target, BaseRecord original) {
    if (!target.canCreateInTarget) {
      return false;
    }
    // need to be able to move from src, and create in target folder.
    boolean canMoveSrc = permissionUtils.isPermitted(original, PermissionType.SEND, target.user);
    if (!canMoveSrc) {
      return false;
    }
    if (target.outsideGroupFolderContext) {
      return true;
    }
    target.resolvePaths();
    // target is in a shared folder: can only move within the same group folder.
    if (!target.newParentPath.isEmpty()) {
      RSPath originalPath =
          original.getShortestPathToParentVia(target.userSharedFolder, null, target.groupFolder());
      if (originalPath.isEmpty()) {
        // can't move from outside group folders into a group folder
        return false;
      }
      int index = DEPTH_FROM_SHAREDROOT_WHERE_SHARINGALLOWED;
      if (originalPath.get(index) == null
          || target.newParentPath.get(index) == null
          || !originalPath.get(index).equals(target.newParentPath.get(index))) {
        return false;
      } // else we're in the same group folder
    }
    return true;
  }
}
