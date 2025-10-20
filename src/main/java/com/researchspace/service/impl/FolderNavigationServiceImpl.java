package com.researchspace.service.impl;

import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.service.FolderManager;
import com.researchspace.service.FolderNavigationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FolderNavigationServiceImpl implements FolderNavigationService {

  private final FolderManager folderManager;
  private final PermissionUtils permissionUtils;

  @Autowired
  public FolderNavigationServiceImpl(FolderManager folderManager, PermissionUtils permissionUtils) {
    this.folderManager = folderManager;
    this.permissionUtils = permissionUtils;
  }

  @Override
  public Optional<Folder> findParentForUser(Long parentId, User user, Folder folder) {
    if (parentId != null) {
      // Validate that the provided parentId is a parent of this folder
      boolean isValidParent =
          folder.getParents().stream()
              .map(RecordToFolder::getFolder)
              .anyMatch(f -> f.getId().equals(parentId));

      if (!isValidParent) {
        throw new IllegalArgumentException(
            String.format("Folder %s is not a parent of %s", parentId, folder.getId()));
      }
      return folderManager.getFolderSafe(parentId, user);
    } else {
      return getOwnerOrSharedParent(user, folder);
    }
  }

  @Override
  public List<Folder> buildPathToRootFolder(Folder folder, User user, Long parentId) {
    List<Folder> pathToRootFolder = new ArrayList<>();
    Optional<Folder> parentFolder = findParentForUser(parentId, user, folder);

    while (parentFolder.isPresent()) {
      Folder currFolder = parentFolder.get();
      pathToRootFolder.add(currFolder);

      // For gallery subfolders, stop at Gallery level to not include the user's root folder
      if (currFolder.hasType(RecordType.ROOT_MEDIA)) {
        break;
      }

      parentFolder = getOwnerOrSharedParent(user, currFolder);
    }

    return pathToRootFolder;
  }

  private boolean isInUsersWorkspace(User user, Folder folder) {
    return folder.getOwner().equals(user) && !folder.isSharedFolder();
  }

  private boolean isSharedFolderWithAccess(User user, Folder folder) {
    return folder.isSharedFolder()
        && permissionUtils.isPermitted(folder, PermissionType.READ, user);
  }

  /**
   * Finds the parent folder context for a given folder and user. Notebooks are a special case that
   * can have multiple parents for the same user (i.e. their workspace and any shared locations).
   * When a notebook has 2 parents for the same user, and parentId is not explicitly provided - and
   * therefore this method gets called for a notebook - this method returns the workspace context
   * first.
   */
  private Optional<Folder> getOwnerOrSharedParent(User user, Folder folder) {
    return folder.getParentFolders().stream()
        .filter(parent -> isInUsersWorkspace(user, parent))
        .findAny()
        .or(
            () ->
                folder.getParentFolders().stream()
                    .filter(parent -> isSharedFolderWithAccess(user, parent))
                    .findAny());
  }
}
