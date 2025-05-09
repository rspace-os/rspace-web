package com.researchspace.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.events.GroupMembershipEvent;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.views.GroupInvitation;
import com.researchspace.model.views.UserView;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;

/**
 * Performs low-level operation on Groups. For easier group creation from UI level consider using
 * {@link IGroupCreationStrategy} methods.
 */
public interface GroupManager {

  /**
   * Persists or updates a group. Equivalent to <code> saveGroup(group, true)</code>. Establishes
   * association with a community if a community id is set into the group.
   */
  Group saveGroup(Group group, User subject);

  /** Get a group by its id */
  Group getGroup(Long groupId);

  /** Gets all the Groups */
  List<Group> list();

  /** Gets all groups that the authenticated user belongs to. */
  Set<Group> listGroupsForUser();

  /**
   * Adds this user to a group. As a side effect, the group folder will be linked to the user's
   * folder tree, <em>unless the new user has a global PI role </em>. This is exclusion is required
   * in order to prevent cycles in the folder tree.
   *
   * @param username the username of the person to add
   * @param groupId The database id of the Group to which the user will be added.
   * @param role The user's role in the group
   * @return The groups that the user was added to.
   */
  Group addUserToGroup(String username, Long groupId, RoleInGroup role)
      throws IllegalAddChildOperation;

  /**
   * Adds a list of members to the group during group creation. Note: doesn't create shared folders,
   * compare with {@link #addUserToGroup(String, Long, RoleInGroup)}.
   *
   * @param grpId
   * @param users The users to add
   * @param pis A comma separated list of usernames to be PI role
   * @param admins A comma separated list of usernames to be admin role
   * @param sessionUser the authenticated user in the session who is performing this operation.
   * @return the group, populated with the added users.
   */
  Group addMembersToGroup(
      Long grpId, List<User> users, String pis, String admins, User sessionUser);

  Group addMembersToProjectGroup(
      Long groupId, List<User> usersToAdd, String owners, User SessionUser);

  /** Removes a user from a group */
  Group removeUserFromGroup(String userNameToRemove, Long grpId, User subject);

  /**
   * Disbands and removes the group, removing shared folder from each user.
   *
   * @return the deleted {@link Group}
   */
  Group removeGroup(Long groupId, User subject);

  /**
   * Returns the group from shareFolder (or any of the subfolders fo the share folder)
   *
   * @param user
   * @param sharedFolder
   * @return Returns the group
   */
  Group getGroupFromAnyLevelOfSharedFolder(User user, Folder sharedFolder);

  /**
   * Changes the role in a group for the specified user
   *
   * @param groupId The group
   * @param userId Username of user whose role is to be modified
   * @param role The new role of the user, should be a String representation of a {@link
   *     RoleInGroup} enum
   */
  void setRoleForUser(long groupId, Long userId, String role, User subject);

  /** Gets paginated listing of groups */
  ISearchResults<Group> list(User subject, PaginationCriteria<Group> pagCrit);

  /**
   * Creates the communal shared folder for this group
   *
   * @return the created group folder, or <code>null</code> if not created.
   */
  Folder createSharedCommunalGroupFolders(Long grpId, String creatorName)
      throws IllegalAddChildOperation;

  List<Group> listGroupsForOwner(User owner);

  /**
   * /** Gets potential members of a collaboration group, which are users who belong to LabGroups
   * where the PI is already a member of the collaboration group. This method does not include
   * existing collaboration group members.
   *
   * @param id A CollaborationGroup id
   */
  List<UserView> getCandidateMembersOfCollabGroup(Long id);

  /**
   * Looks up lab groups that this user belongs to, and removes any members of these groups from the
   * collaboration group. In other words, this removes a labgroup's participation in a collaboration
   * group.
   *
   * @param collabGroupId The collaboration group id
   * @param pi The user doing the removal, who must have Group:Edit permission for the collaboration
   *     group, and will usually be the PI of a lab group that forms part of the collaboration
   *     group.
   */
  void removeLabGroupMembersFromCollabGroup(Long collabGroupId, User pi);

  /**
   * Loads a group with its community already initialised
   *
   * @return A {@link Group} with its community initialised for use outside a Hibernate session, or
   *     <code>null</code> if no such group exists
   */
  Group getGroupWithCommunities(Long groupId);

  /**
   * Overridden alternative to saveGroup that uses a boolean flag to indicate whether the group is
   * new (i.e., transient, nt persisted)
   *
   * @return the saved group
   */
  Group saveGroup(Group group, boolean isNew, User subject);

  /** Boolean test for whether a group exists or not. */
  boolean groupExists(String uniqueName);

  /**
   * Promotes a user to PI, removing him from groups where hes is a member, and making him PI of any
   * collaboration groups he's in. This method assumes that the user has been initialised with a
   * folder etc.
   *
   * @param toPromote the User to promote
   * @return the promoted User
   */
  User promoteUserToPi(User toPromote, User subject);

  List<GroupInvitation> getPendingGroupInvitationsByGroupId(Long groupId);

  List<Group> searchGroups(String term);

  /**
   * Authorise lab admin to view all users work in group.
   *
   * @param labAdminUserId The id of the lab admin whose permissions are being altered
   * @param subject The authenticated user, must be a lab PI
   * @param grpId The group id
   * @param enable <code>true</code> to grant permission, <code>false</code> to revoke.
   * @return the modified lab admin user
   * @throws AuthorizationException if:
   *     <ul>
   *       <li>subject is not a PI of the group
   *       <li><code>labAdminUserId</code> is not a lab admin in the group.
   *     </ul>
   *
   * @throws UnsupportedOperationException if group is not a lab group.
   */
  User authorizeLabAdminToViewAll(Long labAdminUserId, User subject, Long grpId, boolean enable);

  /** Get groups from a {@link Collection} of group Ids */
  List<Group> getGroups(Collection<Long> groupIds);

  /**
   * Authorise PI to edit all users' work in group.
   *
   * @param groupId The group id
   * @param subject The authenticated user, must be a lab PI
   * @param canPIEditAll <code>true</code> to grant permission, <code>false</code> to revoke.
   */
  void authorizePIToEditAll(Long groupId, User subject, boolean canPIEditAll);

  /**
   * Authorise all PIs of this group to edit all users' work in group. This method can be used to
   * revoke the permission when community admin or system admin disables this feature.
   *
   * @param groupId The group id
   * @param subject The authenticated user
   * @param canPIEditAll <code>true</code> to grant permission, <code>false</code> to revoke.
   */
  void authorizeAllPIsToEditAll(Long groupId, User subject, boolean canPIEditAll);

  /** Mark group profile as hidden from public listings. */
  Group hideGroupProfile(Boolean hideProfile, Group group, User subject);

  /**
   * Replaces current PI with new PI. The old PI reverts back to 'User' role. Preconditions:
   *
   * <ul>
   *   <li>New PI must be a member of the group
   *   <li>New PI can't be same user as current PI
   *   <li>Must be a LabGroup
   *   <li>New PI must have global PI role
   * </ul>
   *
   * @param admin the subject
   * @return the updated group
   * @throws IllegalArgumentException if any precondition not met.
   */
  Group setNewPi(long groupId, Long newPiId, User admin);

  List<UserGroup> findByUserId(Long userId);

  /** Gets GroupMembershipEvent for user */
  List<GroupMembershipEvent> getGroupEventsForUser(User sessionUser, User toRetrieve);

  /** Gets GroupMembershipEvents for a group */
  List<GroupMembershipEvent> getGroupEventsForGroup(User sessionUser, Group toRetrieve);

  /**
   * Sets group autosharing as enabled for a user.
   *
   * @return the updated Group
   */
  Group enableAutoshareForUser(User user, Long groupId);

  /**
   * Sets group autosharing as disabled for a user
   *
   * @return the updated Group
   */
  Group disableAutoshareForUser(User user, Long groupId);

  /**
   * Creates an autoshare folder for user in group, using a default name for the folder
   *
   * @throws IllegalArgumentException if user is not in group.
   */
  Folder createAutoshareFolder(User user, Group group);

  /** Creates an autoshare folder for user in group with a given folderName */
  Folder createAutoshareFolder(User user, Group group, String folderName);

  Group getGroupByCommunalGroupFolderId(Long communalGroupFolderId);
}
