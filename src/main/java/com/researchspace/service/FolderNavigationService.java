package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import java.util.List;
import java.util.Optional;

/**
 * Service for generating folder paths to root and resolving parent folders, handling special cases
 * such as notebooks and shared folders.
 */
public interface FolderNavigationService {

  /**
   * Finds the appropriate parent folder for a given folder in the context of a specific user.
   * Handles cases where folders have multiple parents, such as notebooks (in the user's workspace
   * and any shared locations) and group shared folders (e.g. groupA_SHARED), which has a parent per
   * user in the group (e.g. their 'Lab Groups' folder).
   *
   * @param parentId Optional parent ID to use (required for shared notebooks, where a notebook
   *     could have multiple parents owned by the same user (e.g. the user's workspace and any
   *     shared locations))
   * @param user The user viewing the folder
   * @param folder The folder to find the parent for
   * @return The parent folder in the appropriate context, or empty if no accessible parent
   * @throws IllegalArgumentException if parentId is provided but is not a valid parent of the
   *     folder
   */
  Optional<Folder> findParentForUser(Long parentId, User user, Folder folder);

  /**
   * Convenience method for finding the parent folder when parentId is not known.
   *
   * @param user The user viewing the folder
   * @param folder The folder to find the parent of
   * @return The parent folder in the appropriate context, or empty if no accessible parent
   */
  Optional<Folder> findParentForUser(User user, Folder folder);

  /**
   * Builds the path from a folder to its root (Workspace or Gallery root) folder for a given user.
   * The path will stop at ROOT_MEDIA for gallery subfolders.
   *
   * @param folder The starting folder
   * @param user The user viewing the folder
   * @param parentId Optional explicit parent ID for disambiguation of shared notebooks
   * @return List of folders from parent to root (not including the starting folder)
   */
  List<Folder> buildPathToRootFolder(Folder folder, User user, Long parentId);
}
