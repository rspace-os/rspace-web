package com.researchspace.service.impl;

import com.researchspace.dao.FolderDao;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;
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

  protected boolean checkMovePermissions(User user, Folder newparent, BaseRecord original) {
    boolean canCreateInTarget =
        permissionUtils.isPermitted(newparent, PermissionType.FOLDER_RECEIVE, user);
    if (!canCreateInTarget) {
      return false;
    }
    // need to be able to move from src, and create in target folder.
    boolean canMoveSrc = permissionUtils.isPermitted(original, PermissionType.SEND, user);
    if (!canMoveSrc) {
      return false;
    }
    // short circuit if target isn't a group folder( which has system type)
    // i.e., if the permission are OK, and we're not in  group folder, then we we can move
    if (!newparent.isSystemFolder() || newparent.isRootFolderForUser(user)) {
      return true;
    }
    Folder userSharedFlder = folderDao.getUserSharedFolder(user);
    // now, check if we're sharing within a group folder:
    RSPath newParentPath = newparent.getShortestPathToParent(userSharedFlder);

    // target is in a shared folder.
    if (!newParentPath.isEmpty()) {
      Folder grpFolder =
          (Folder) newParentPath.findFirstByType(RecordType.SHARED_GROUP_FOLDER_ROOT).get();
      RSPath originalPath = original.getShortestPathToParentVia(userSharedFlder, null, grpFolder);
      if (!originalPath.isEmpty()) {
        int index = DEPTH_FROM_SHAREDROOT_WHERE_SHARINGALLOWED;
        // can only move within a group folder.
        if (originalPath.get(index) == null
            || newParentPath.get(index) == null
            || !originalPath.get(index).equals(newParentPath.get(index))) {
          return false;
        } // else we're in the same group folder

      } else {
        // can't move from outside group folders into a group folder
        return false;
      }
    }
    return true;
  }
}
