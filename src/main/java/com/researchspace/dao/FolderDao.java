package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.TreeViewItem;
import java.util.List;
import java.util.Optional;

public interface FolderDao extends GenericDao<Folder, Long> {

  /**
   * Ease of use method returns the root record always
   *
   * @return
   * @see https://ops.researchspace.com/globalId/SD4529
   */
  Folder getRootRecordForUser(User user);

  public List<Long> getRecordIds(Folder fd);

  /**
   * Get Gallery folder child of user's root folder.
   *
   * @param user user;
   * @return media Folder from User, or <code>null</code> if could not be found.
   */
  public Folder getGalleryFolderForUser(User user);

  /**
   * Get Template folder child of user's root folder.
   *
   * @param user;
   * @return Template Folder from User, or <code>null</code> if could not be found.
   */
  public Folder getTemplateFolderForUser(User user);

  /**
   * Gets the user's LabGroup folder or <code>null</code> if it doesn't exist.
   *
   * @param u user
   * @return The user's lab group folder.
   */
  public Folder getLabGroupFolderForUser(User u);

  /**
   * Gets the user's IndividualSharedItems folder or <code>null</code> if it doesn't exist.
   *
   * @param u user
   * @return The user's lab group folder.
   */
  public Folder getIndividualSharedItemsFolderForUser(User u);

  /**
   * Gets the user's Collaboration group's folder or <code>null</code> if it doesn't exist.
   *
   * @param u user
   * @return The user's lab group folder.
   */
  public Folder getCollaborationGroupsSharedFolderForUser(User u);

  /**
   * Gets the user's ProjectGroups shared folder or <code>null</code> if it doesn't exist.
   *
   * @param u- The
   * @return The user's shared ProjectGroups.
   */
  public Folder getProjectGroupsSharedFolderForUser(User u);

  /**
   * Gets a shared group folder for a particular group
   *
   * @param group
   * @return
   */
  public Folder getSharedFolderForGroup(Group group);

  Folder getSharedSnippetFolderForGroup(Group group);

  /**
   * Gets the shared folder between two users, or <code>null</code> if it doesn't exist.
   *
   * @param sharer
   * @param sharee
   * @return
   */
  Folder getIndividualSharedFolderForUsers(User sharer, User sharee, BaseRecord docOrNotebook);

  /**
   * Gets the top level 'Shared' folder in user's workspace
   *
   * @param u user
   * @return
   */
  Folder getUserSharedFolder(User u);

  /**
   * Gets a System folder with given name owned by the user
   *
   * @param user
   * @param folderName
   * @return The requested folder or <code>null</code> if not found.
   */
  Folder getSystemFolderForUserByName(User user, String folderName);

  Optional<Folder> getApiFolderForContentType(String contentType, User subject);

  /**
   * Read-only query to get data to populate workspace TreeView
   *
   * @param folderId id of parent folder to load
   * @param pgCrit Criteria
   * @return ISearchResults<TreeViewItem>
   * @see https://ops.researchspace.com/globalId/SD4531
   */
  ISearchResults<TreeViewItem> getFolderListingForTreeView(
      Long folderId, PaginationCriteria<TreeViewItem> pgCrit);

  Optional<Folder> getApiInboxSubFolderByName(Folder apiFolder, String folderName, User subject);

  /**
   * Gets the Import folder, if it exists
   *
   * @param subject
   * @return
   */
  Optional<Folder> getImportFolder(User subject);

  Long getLabGroupFolderIdForUser(User user);
}
