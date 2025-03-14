package com.researchspace.service.impl;

import static com.researchspace.service.UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX;

import com.researchspace.Constants;
import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.ObjectToStringPropertyTransformer;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.CollaborationGroupTrackerDao;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.DAOUtils;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.GroupDao;
import com.researchspace.dao.GroupMembershipEventDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.dao.RoleDao;
import com.researchspace.dao.UserDao;
import com.researchspace.dao.UserGroupDao;
import com.researchspace.model.CollabGroupCreationTracker;
import com.researchspace.model.Community;
import com.researchspace.model.DefaultGroupNamingStrategy;
import com.researchspace.model.Group;
import com.researchspace.model.IGroupNamingStrategy;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.SwapPiCommand;
import com.researchspace.model.dtos.SwapPiValidator;
import com.researchspace.model.events.GroupEventType;
import com.researchspace.model.events.GroupMembershipEvent;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.PropertyConstraint;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.ChildAddPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.views.GroupInvitation;
import com.researchspace.model.views.GroupInvitation.Invitee;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.UserView;
import com.researchspace.service.FolderManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IContentInitialiserUtils;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.PiChangeContext;
import com.researchspace.service.PiChangeHandler;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.UserFolderCreator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.Permission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

@Service("grpManager")
@Lazy /* marking with @Lazy as class is involved in some bean autowire cycles */
@Slf4j
public class GroupManagerImpl implements GroupManager {

  private @Autowired PermissionFactory permFactory;
  private @Autowired GroupDao groupDao;
  private @Autowired CollaborationGroupTrackerDao collabGrpTrackerDao;

  private @Autowired CommunicationDao communicationDao;
  private @Autowired CommunityDao communityDao;
  private @Autowired DAOUtils daoUtils;
  private @Autowired IGroupPermissionUtils groupPermUtils;
  private @Autowired RoleDao roleDao;
  private @Autowired OperationFailedMessageGenerator authMsgGenerator;
  private @Autowired PiChangeHandler piChangeHandler;
  private @Autowired IControllerInputValidator inputValidator;
  private @Autowired FolderManager folderMgr;
  private @Autowired IRecordFactory recordFactory;
  private @Autowired IPermissionUtils permissnUtils;
  private @Autowired UserGroupDao ugDao;
  private @Autowired RecordGroupSharingDao groupshareRecordDao;
  private @Autowired RecordSharingManager sharingManager;
  private @Autowired UserDao userDao;
  private @Autowired FolderDao folderDao;
  private @Autowired GroupMembershipEventDao groupMembershipEventDao;
  private @Autowired RecordManager recordManager;

  private @Autowired IContentInitialiserUtils contentUtils;

  public GroupManagerImpl() {
    permFactory = new DefaultPermissionFactory();
  }

  @Override
  public Group saveGroup(Group group, boolean isNew, User subject) {
    if (!isNew) {
      if (!permissnUtils.isPermitted(group, PermissionType.WRITE, subject)) {
        throw new AuthorizationException(authMsgGenerator.getFailedMessage(subject, "edit group"));
      }
    }
    group = groupDao.save(group);
    // only add to community if it's not already ina community
    if (group.getCommunityId() != null && isNew) {
      Community comm = communityDao.get(group.getCommunityId());
      comm.addLabGroup(group);
      communityDao.save(comm);
    }
    // rename group folder in case group name has changed RSPAC-315.
    if (group.isGroupFolderCreated()) { // group must alraeady be created
      Folder grpFolder = folderDao.getSharedFolderForGroup(group);
      if (grpFolder != null) {
        IGroupNamingStrategy strategy = new DefaultGroupNamingStrategy();
        grpFolder.setName(strategy.getSharedGroupName(group));
        folderDao.save(grpFolder);
      }
    }
    if (isNew) {
      for (User member : group.getMembers()) {
        notifyUserAddedToGroup(group, member);
      }
    }
    return group;
  }

  @Override
  public Group saveGroup(Group group, User subject) {
    return saveGroup(group, true, subject);
  }

  public Group addUserToGroup(String usernameToAdd, Long groupId, RoleInGroup role)
      throws IllegalAddChildOperation {

    Group group = groupDao.get(groupId);
    User userToAdd = userDao.getUserByUsername(usernameToAdd);
    Set<ConstraintBasedPermission> grpRolePerms = getPermissionsForRole(role, group);
    // if group already has user, just return
    if (group.hasMember(userToAdd)) {
      return group;
    }
    group.addMember(userToAdd, role, grpRolePerms);
    permissnUtils.notifyUserOrGroupToRefreshCache(userToAdd);

    Set<User> labAdmins = group.getLabAdminsWithViewAllPermission();
    for (User admin : labAdmins) {
      createRecordViewPermissionByOwner(admin, TransformerUtils.toSet(userToAdd));
      userDao.save(admin);
      permissnUtils.notifyUserOrGroupToRefreshCache(admin);
    }

    if (group.getCommunalGroupFolderId() != null) {
      Folder grpFolder = folderDao.getSharedFolderForGroup(group);
      Folder labFolder = getSharedGroupFolderParentForUser(group, userToAdd);
      labFolder.addChild(
          grpFolder, ChildAddPolicy.DEFAULT, userToAdd, ACLPropagationPolicy.NULL_POLICY);
      folderDao.save(labFolder);
      folderDao.save(grpFolder);

      // now we add this new group member's home folder to group admin's folders
      addMemberFoldersToGroupAdminsFolders(TransformerUtils.toSet(userToAdd), group);
    }
    if (group.getSharedSnippetGroupFolderId() != null) {
      Folder sharedSnippetFolder = folderDao.get(group.getSharedSnippetGroupFolderId());
      String groupFolderName =
          group.isCollaborationGroup()
              ? Folder.COLLABORATION_GROUPS_FLDER_NAME
              : group.isProjectGroup()
                  ? Folder.PROJECT_GROUPS_FOLDER_NAME
                  : Folder.LAB_GROUPS_FOLDER_NAME;
      String parentFolderName = SHARED_SNIPPETS_FOLDER_PREFIX + groupFolderName;
      Folder parentForSharedSnippetFolder =
          folderDao.getSystemFolderForUserByName(userToAdd, parentFolderName);
      parentForSharedSnippetFolder.addChild(
          sharedSnippetFolder, ChildAddPolicy.DEFAULT, userToAdd, ACLPropagationPolicy.NULL_POLICY);
      folderDao.save(parentForSharedSnippetFolder);
    }

    notifyUserAddedToGroup(group, userToAdd);
    return group;
  }

  private void notifyUserAddedToGroup(Group group, User userToAdd) {
    groupMembershipEventDao.save(new GroupMembershipEvent(userToAdd, group, GroupEventType.JOIN));
  }

  private Set<ConstraintBasedPermission> getPermissionsForRole(RoleInGroup role, Group g) {
    Set<ConstraintBasedPermission> grpRolePerms = new HashSet<ConstraintBasedPermission>();

    if (RoleInGroup.PI.equals(role) && g.isLabGroup()) {
      grpRolePerms = permFactory.createDefaultPermissionsForGroupPI(g);
    } else if (RoleInGroup.PI.equals(role) && g.isCollaborationGroup()) {
      grpRolePerms = permFactory.createDefaultPermissionsForCollabGroupPI(g);
    } else if (RoleInGroup.RS_LAB_ADMIN.equals(role) && g.isLabGroup()) {
      grpRolePerms = permFactory.createDefaultPermissionsForGroupAdmin(g);
    } else if (RoleInGroup.RS_LAB_ADMIN.equals(role) && g.isCollaborationGroup()) {
      grpRolePerms = permFactory.createDefaultPermissionsForCollabGroupAdmin(g);
    } else if (RoleInGroup.GROUP_OWNER.equals(role) && g.isProjectGroup()) {
      grpRolePerms = permFactory.createDefaultPermissionsForProjectGroupOwner(g);
    }
    return grpRolePerms;
  }

  public Group removeUserFromGroup(String uname, Long grpId, User subject) {

    Group grp = groupDao.get(grpId);
    User userToRemove = groupPermUtils.assertLeaveGroupPermissions(uname, subject, grp);

    List<RecordGroupSharing> sharedinGrp =
        sharingManager.getSharedRecordsForUserAndGroup(userToRemove, grp);

    // unshare the records with group
    for (RecordGroupSharing recordSharing : sharedinGrp) {
      ShareConfigElement configEl =
          new ShareConfigElement(grpId, recordSharing.getPermType().toString());
      ShareConfigElement[] config = new ShareConfigElement[] {configEl};
      sharingManager.unshareRecord(userToRemove, recordSharing.getShared().getId(), config);
    }

    /* TODO: records shared with individual group members are kept shared after removing from group,
     * that's probably not right and is raised as RSPAC-2047. */

    if (grp.getCommunalGroupFolderId() != null) {
      Folder communalGrpFolder = folderDao.get(grp.getCommunalGroupFolderId());
      removeCommunalFolderFromParent(communalGrpFolder, uname);
      /* now we remove this user's home folder from group pis' labgroup folder */
      removeUserFolderFromPIFolders(userToRemove, grp);

      Folder communalGrpSnippetFolder = folderDao.get(grp.getSharedSnippetGroupFolderId());
      removeCommunalFolderFromParent(communalGrpSnippetFolder, uname);
    }

    // now remove labAdmins permission to view this user's docs
    Set<User> labAdmins = grp.getLabAdminsWithViewAllPermission();
    removeUserFoldersAndLabAdminPermissions(TransformerUtils.toSet(userToRemove), labAdmins, grp);

    UserGroup ug = grp.getUserGroupForUser(userToRemove);
    // if user is a lab admin in this group (RSPAC-1116)
    if (ug != null && ug.isAdminRole()) {
      setRoleForUser(grp.getId(), userToRemove.getId(), RoleInGroup.DEFAULT.toString(), subject);
    }

    grp.removeMember(userToRemove);
    groupDao.save(grp);
    if (ug != null) {
      ugDao.remove(ug.getId());
    }
    userDao.save(userToRemove); // persist UserGroup removal
    permissnUtils.notifyUserOrGroupToRefreshCache(userToRemove);
    groupMembershipEventDao.save(
        new GroupMembershipEvent(userToRemove, grp, GroupEventType.REMOVE));
    return grp;
  }

  private void removeCommunalFolderFromParent(Folder communalGrp, String uname) {
    for (RecordToFolder r2f : communalGrp.getParents()) {
      if (r2f.getUserName().equals(uname)) {
        r2f.getFolder().removeChild(communalGrp);
        folderDao.save(r2f.getFolder());
        break;
      }
    }
    folderDao.save(communalGrp);
  }

  private void removeUserFoldersAndLabAdminPermissions(
      Set<User> usersToRemove, Set<User> labAdmins, Group grp) {
    for (User admin : labAdmins) {
      removeUsersFromLabAdminPermissions(usersToRemove, admin, grp);
    }
    if (grp.isLabGroup()) {
      removeMemberFoldersFromAdminsLabGroupsFolder(usersToRemove, grp, labAdmins);
    }
  }

  // return updated admin user
  private User removeUsersFromLabAdminPermissions(Set<User> usersToRemove, User admin, Group grp) {
    for (User user : usersToRemove) {

      // if 'admin' has Lab Admin View All role in another group, then permissions stay
      if (hasPiOrLabAdminViewAllRoleInOtherGroup(admin, user, grp, false, true)) {
        continue;
      }

      Set<Permission> perms = admin.getAllPermissions(true, false);
      Iterator<Permission> it = perms.iterator();
      while (it.hasNext()) {
        Permission p = it.next();
        ConstraintBasedPermission perm = (ConstraintBasedPermission) p;
        if (PermissionDomain.RECORD.equals(perm.getDomain())
            && PermissionType.READ.equals(perm.getActions().iterator().next())) {
          Map<String, PropertyConstraint> propConstraints = perm.getPropertyConstraints();
          PropertyConstraint byOwner = propConstraints.get("owner");
          if (byOwner != null) {
            admin.removePermission(perm);
            boolean removed = byOwner.removeValue(user.getUsername());

            if (!removed) {
              ConstraintBasedPermission altered = getAlteredPermission(byOwner);
              admin.addPermission(altered);
            }
          }
        }
      }
    }
    userDao.save(admin);
    permissnUtils.notifyUserOrGroupToRefreshCache(admin);
    return admin;
  }

  private ConstraintBasedPermission getAlteredPermission(PropertyConstraint byOwner) {
    ConstraintBasedPermission cbp =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    cbp.addPropertyConstraint(byOwner);
    return cbp;
  }

  @Override
  public Group getGroup(Long groupId) {
    return groupDao.get(groupId);
  }

  @Override
  public List<Group> list() {
    return groupDao.getAllDistinct();
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public ISearchResults<Group> list(User subject, PaginationCriteria<Group> pgCrit) {
    FilterCriteria searchCrit = pgCrit.getSearchCriteria();
    if (searchCrit != null && !(searchCrit instanceof GroupSearchCriteria)) {
      throw new IllegalArgumentException(
          "Group listings, if they use a search criteria, must use a GroupSearchCriteria");
    }

    // restrict by community
    if (isCommunityAdmin(subject, searchCrit)) {
      List<Community> communities = communityDao.listCommunitiesForAdmin(subject.getId());
      // admin user is not in community, return empty list
      if (communities.isEmpty()) {
        return new SearchResultsImpl<Group>(Collections.EMPTY_LIST, pgCrit, 0L);
      } else {
        Community comm = communities.get(0);
        ((GroupSearchCriteria) searchCrit).setCommunityId(comm.getId());
      }
    }
    return groupDao.list(pgCrit);
  }

  private boolean isCommunityAdmin(User subject, FilterCriteria searchCrit) {
    return subject.hasRole(Role.ADMIN_ROLE)
        && searchCrit != null
        && ((GroupSearchCriteria) searchCrit).isFilterByCommunity();
  }

  public Folder createSharedCommunalGroupFolders(Long grpId, String creatorName)
      throws IllegalAddChildOperation {
    User creator = userDao.getUserByUsername(creatorName);
    Group grp = groupDao.get(grpId);
    Folder grpFolder = null;
    if (grp.isGroupFolderWanted() && !grp.isGroupFolderCreated()) {
      grpFolder = recordFactory.createCommunalGroupFolder(grp, creator);
      folderDao.save(grpFolder);
      grp.setCommunalGroupFolderId(grpFolder.getId());
      groupDao.save(grp);
      permFactory.setUpACLForGroupSharedRootFolder(grp, grpFolder);
      grp.setGroupFolderCreated();

      for (User u : grp.getMembers()) {
        Long rootId = getSharedGroupFolderParentForUser(grp, u).getId();
        folderMgr.addChild(rootId, grpFolder, u, ACLPropagationPolicy.NULL_POLICY);
        log.info("Added {} to group {}", u.getUsername(), grp.getUniqueName());
      }
      // add in group member's root folder to PI's folder tree
      addMemberFoldersToGroupAdminsFolders(grp.getAllNonPIMembers(), grp);
    }
    // add shared snippets folder IF it has not already been created
    if (grp.getSharedSnippetGroupFolderId() == null) {
      Folder grpSharedSnippetFolder = recordFactory.createCommunalGroupSnippetFolder(grp, creator);
      folderDao.save(grpSharedSnippetFolder);
      grp.setSharedSnippetGroupFolderId(grpSharedSnippetFolder.getId());
      groupDao.save(grp);
      permFactory.setUpACLForGroupSharedRootFolder(grp, grpSharedSnippetFolder);
      groupDao.save(grp);
      for (User user : grp.getMembers()) {
        Folder snippetFolder =
            recordManager.getGallerySubFolderForUser(Folder.SNIPPETS_FOLDER, user);
        // this checks to see if a 'SHARED/LABGroup' (or CollaborationGroup) folder has been created
        // inside the user's SNIPPETS folder
        // If it has then this group shared folder is added as a child to the user's folder
        // 'SNIPPETS/SHARED/LAbGroup or CollaborationGroup
        Folder snippetSharedFolder =
            snippetFolder.getSubFolderByName(
                SHARED_SNIPPETS_FOLDER_PREFIX + Folder.SHARED_FOLDER_NAME);
        if (snippetSharedFolder != null) {
          Folder parentForSharedSnippets = null;
          if (grp.isCollaborationGroup()) {
            parentForSharedSnippets =
                snippetSharedFolder.getSubFolderByName(
                    SHARED_SNIPPETS_FOLDER_PREFIX + Folder.COLLABORATION_GROUPS_FLDER_NAME);
          } else if (grp.isProjectGroup()) {
            parentForSharedSnippets =
                snippetSharedFolder.getSubFolderByName(
                    SHARED_SNIPPETS_FOLDER_PREFIX + Folder.PROJECT_GROUPS_FOLDER_NAME);
          } else {
            parentForSharedSnippets =
                snippetSharedFolder.getSubFolderByName(
                    SHARED_SNIPPETS_FOLDER_PREFIX + Folder.LAB_GROUPS_FOLDER_NAME);
          }
          folderMgr.addChild(
              parentForSharedSnippets.getId(),
              grpSharedSnippetFolder,
              user,
              ACLPropagationPolicy.NULL_POLICY);
        }
        log.info("Added shared snippets folder to group {}", grp.getUniqueName());
      }
      // ******DO NOT REMOVE - otherwise shared snippet folder will not have ACL persisted when
      // update in liquibase
      // profile. I do not know why. In Run, prod profiles the transaction boundaries when
      // GroupSharedSnippetsFolderAppInitialiser calls groupManager.
      //                                createSharedCommunalGroupFolders
      // work as expected but not in liquibase profile.
      // I was not able to create an automated test for this, as the transaction boundaries also
      // behaved as expected in all automated tests.
      folderDao.save(grpSharedSnippetFolder);
      // ******DO NOT REMOVE
    }

    return grpFolder;
  }

  private Folder getSharedGroupFolderParentForUser(Group grp, User u) {
    if (grp.isLabGroup()) {
      return folderDao.getLabGroupFolderForUser(u);
    }
    if (grp.isProjectGroup()) {
      Folder projectGroupsShared = folderDao.getProjectGroupsSharedFolderForUser(u);
      if (projectGroupsShared == null) {
        projectGroupsShared = createProjectGroupsSharedFolders(grp, u);
      }
      return projectGroupsShared;
    }
    return folderDao.getCollaborationGroupsSharedFolderForUser(u);
  }

  private Folder createProjectGroupsSharedFolders(Group grp, User u) {
    Folder sharedFolder = folderDao.getUserSharedFolder(u);
    Folder projectGroupsShared =
        recordFactory.createSystemCreatedFolder(Folder.PROJECT_GROUPS_FOLDER_NAME, u);
    ConstraintBasedPermission cbp =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    ACLElement element = new ACLElement(u.getUsername(), cbp);
    projectGroupsShared.getSharingACL().addACLElement(element);
    contentUtils.addChild(sharedFolder, projectGroupsShared, u);

    Folder snippetFolder = recordManager.getGallerySubFolderForUser(Folder.SNIPPETS_FOLDER, u);
    Folder snippetSharedFolder;
    if (snippetFolder.getSubFolderByName(SHARED_SNIPPETS_FOLDER_PREFIX + Folder.SHARED_FOLDER_NAME)
        == null) {
      snippetSharedFolder =
          recordFactory.createSystemCreatedFolder(
              SHARED_SNIPPETS_FOLDER_PREFIX + Folder.SHARED_FOLDER_NAME, u);
    } else {
      snippetSharedFolder =
          snippetFolder.getSubFolderByName(
              UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX + Folder.SHARED_FOLDER_NAME);
    }
    Folder projectGroupsSnippets =
        recordFactory.createSystemCreatedFolder(
            UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX + Folder.PROJECT_GROUPS_FOLDER_NAME, u);
    projectGroupsSnippets.getSharingACL().addACLElement(element);
    contentUtils.addChild(snippetSharedFolder, projectGroupsSnippets, u);

    return projectGroupsShared;
  }

  /* after adding user to group */
  private void addMemberFoldersToGroupAdminsFolders(Set<User> members, Group group) {
    if (group.isLabGroup()) {
      addMemberFoldersToAdminsLabGroupsFolder(members, getGroupAdminsWhoCanSeeMemberFolders(group));
    }
  }

  /* adds member folders to Shared -> LabGroups folder of PI and Lab Admin with 'View All' permission */
  private void addMemberFoldersToAdminsLabGroupsFolder(Set<User> members, Set<User> admins)
      throws IllegalAddChildOperation {

    for (User admin : admins) {
      Folder adminLabGroupsFolder = folderDao.getLabGroupFolderForUser(admin);
      for (User addedMember : members) {
        if (addedMember.equals(admin)) {
          log.info("excluding self-add home folder of {} ", addedMember.getUsername());
          continue; // skip yourself
        }
        if (addedMember.hasRole(Role.PI_ROLE)) {
          log.info(
              "excluding adding home folder of {} to {}'s lab group folder because {} has global PI"
                  + " role",
              addedMember.getUsername(),
              admin.getUsername(),
              addedMember.getUsername());
          continue; // RSPAC-373
        }

        Folder groupMemberRoot = folderDao.getRootRecordForUser(addedMember);
        groupMemberRoot = daoUtils.initializeAndUnproxy(groupMemberRoot);

        groupMemberRoot.getId();
        groupMemberRoot.getParents().size();

        if (!adminLabGroupsFolder.getChildrens().contains(groupMemberRoot)) {

          ServiceOperationResult<Folder> result =
              folderMgr.addChild(
                  adminLabGroupsFolder.getId(),
                  groupMemberRoot,
                  admin,
                  ACLPropagationPolicy.NULL_POLICY,
                  true);
          if (result.isSucceeded()) {
            log.info(
                "Added {}'s  folder to PI/LA's Shared folder - {}",
                addedMember.getUsername(),
                admin.getUsername());
          } else {
            log.error(
                "GROUP: Could not add {}'s home folder to {}'s LabGroups folder - {}",
                addedMember.getUsername(),
                admin.getUsername(),
                result.getMessage());
          }
        }
      }
    }
  }

  /* after removing a user from a lab group */
  private void removeUserFolderFromPIFolders(User member, Group group) {
    if (group.isLabGroup()) {
      removeMemberFoldersFromAdminsLabGroupsFolder(
          TransformerUtils.toSet(member), group, group.getPiusers());
    }
  }

  /* removes member folders from Shared -> LabGroups folder of PI and Lab Admins with 'View All' permission */
  private void removeMemberFoldersFromAdminsLabGroupsFolder(
      Set<User> members, Group group, Set<User> admins) {
    for (User admin : admins) {
      Long adminLabGroupFolderId = folderDao.getLabGroupFolderForUser(admin).getId();
      for (User member : members) {
        if (member.equals(admin)) {
          continue; // skip yourself
        }
        if (hasPiOrLabAdminViewAllRoleInOtherGroup(admin, member, group, true, true)) {
          continue;
        }
        Folder memberRoot = member.getRootFolder();
        if (memberRoot != null) {
          folderMgr.removeBaseRecordFromFolder(
              memberRoot, adminLabGroupFolderId, ACLPropagationPolicy.NULL_POLICY);
        }
      }
    }
  }

  /**
   * Method for checking if 'userA' has a PI or Lab Admin View All role in any groups that 'userB'
   * belongs to, other than 'group'.
   */
  private boolean hasPiOrLabAdminViewAllRoleInOtherGroup(
      User userA, User userB, Group group, boolean piRoleCheck, boolean labAdminViewAllRoleCheck) {

    Set<Group> commonGroups = getCommonGroupsForUsers(userA, userB);
    commonGroups.remove(group);
    for (Group g : commonGroups) {
      UserGroup roleInG = g.getUserGroupForUser(userA);
      if (piRoleCheck && roleInG.isPIRole()) {
        return true;
      }
      if (labAdminViewAllRoleCheck && roleInG.isAdminRole() && roleInG.isAdminViewDocsEnabled()) {
        return true;
      }
    }
    return false;
  }

  private Set<Group> getCommonGroupsForUsers(User userA, User userB) {
    Set<Group> result = new HashSet<Group>();
    Set<Group> groupsOfA = userA.getGroups();
    Set<Group> groupsOfB = userB.getGroups();
    if (groupsOfA != null && groupsOfB != null) {
      result.addAll(groupsOfA);
      result.retainAll(groupsOfB);
    }
    return result;
  }

  private Set<User> getGroupAdminsWhoCanSeeMemberFolders(Group group) {
    Set<User> adminsWithViewAll = group.getPiusers();
    adminsWithViewAll.addAll(group.getLabAdminsWithViewAllPermission());
    return adminsWithViewAll;
  }

  @Override
  public Group addMembersToProjectGroup(
      Long groupId, List<User> usersToAdd, String owners, User SessionUser) {
    Group grp = getGroup(groupId);
    grp.setGroupOwners(owners);
    for (User userToAdd : usersToAdd) {
      RoleInGroup role = RoleInGroup.DEFAULT;
      if (isAssignedGroupOwner(userToAdd, grp)) {
        role = RoleInGroup.GROUP_OWNER;
      }
      boolean added = grp.addMember(userToAdd, role, getPermissionsForRole(role, grp));
      ugDao.save(grp.getUserGroupForUser(userToAdd));
      if (added) { // avoid duplicate events if user already is in group
        notifyUserAddedToGroup(grp, userToAdd);
      }
    }
    // temporary field for ui.
    grp.getMemberString().clear();
    groupDao.save(grp);
    permissnUtils.notifyUserOrGroupToRefreshCache(grp);
    return grp;
  }

  @Override
  public Group addMembersToGroup(
      Long grpid, List<User> usersToAdd, String pis, String admins, User grpCreator) {
    Group grp = getGroup(grpid);
    grp.setAdmins(admins);
    grp.setPis(pis);
    for (User userToAdd : usersToAdd) {
      RoleInGroup role = RoleInGroup.DEFAULT;
      if (isAssignedPI(userToAdd, grp)) {
        if (!userToAdd.hasRole(Role.PI_ROLE)) {
          throw new IllegalArgumentException(
              String.format("User %s does not have PI role!", userToAdd.getFullName()));
        }
        role = RoleInGroup.PI;
      } else if (isAssignedAdmin(userToAdd, grp)) {
        role = RoleInGroup.RS_LAB_ADMIN;
      }
      boolean added = grp.addMember(userToAdd, role, getPermissionsForRole(role, grp));
      ugDao.save(grp.getUserGroupForUser(userToAdd));
      if (added) { // avoid duplicate events if user already is in group
        notifyUserAddedToGroup(grp, userToAdd);
      }
    }
    // this is a temporary field for ui.
    grp.getMemberString().clear();
    groupDao.save(grp);
    permissnUtils.notifyUserOrGroupToRefreshCache(grp);
    return grp;
  }

  private boolean isAssignedPI(User u, Group grp) {
    if (!StringUtils.isBlank(grp.getPis())) {
      String[] pis = grp.getPis().split(",");
      for (String pi : pis) {
        if (u.getUsername().equals(pi)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isAssignedAdmin(User u, Group grp) {
    if (!StringUtils.isBlank(grp.getAdmins())) {
      String[] admins = grp.getAdmins().split(",");
      for (String admin : admins) {
        if (u.getUsername().equals(admin)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isAssignedGroupOwner(User u, Group grp) {
    if (!StringUtils.isBlank(grp.getGroupOwners())) {
      String[] groupOwners = grp.getGroupOwners().split(",");
      return Arrays.stream(groupOwners).anyMatch(groupOwner -> u.getUsername().equals(groupOwner));
    }
    return false;
  }

  @Override
  public Group removeGroup(Long groupId, User subject) {
    Group group = groupDao.get(groupId);
    // remove any permissions from admins
    Set<User> admins = group.getLabAdminsWithViewAllPermission();
    removeUserFoldersAndLabAdminPermissions(group.getAllNonPIMembers(), admins, group);

    Set<UserGroup> ugs = new HashSet<>(group.getUserGroups());
    for (UserGroup ug : ugs) {
      User u = ug.getUser();
      if (!u.equals(subject)) {
        removeUserFromGroup(u.getUsername(), groupId, subject);
      }
    }
    removeUserFromGroup(subject.getUsername(), groupId, subject);
    groupshareRecordDao.removeAllForGroup(group);
    if (group.isCollaborationGroup()) {
      CollabGroupCreationTracker todelete = collabGrpTrackerDao.getByGroupId(group);
      if (todelete != null) {
        collabGrpTrackerDao.remove(todelete.getId());
      }
    }
    Community community = group.getCommunity();
    if (community != null) {
      community.removeLabGroup(group);
      communityDao.save(community);
    }

    // remove join lab group requests, there is no use for them without a group
    List<GroupMessageOrRequest> groupMsges =
        communicationDao.getGroupMessageByGroupId(group.getId());
    for (GroupMessageOrRequest req : groupMsges) {
      communicationDao.remove(req.getId());
    }

    // remove join/leave events
    List<GroupMembershipEvent> events = groupMembershipEventDao.getGroupEventsForGroup(group);
    for (GroupMembershipEvent event : events) {
      groupMembershipEventDao.remove(event.getId());
    }

    // should be last call once all FKs are removed
    groupDao.remove(group.getId());
    return group;
  }

  @Override
  public List<GroupInvitation> getPendingGroupInvitationsByGroupId(Long groupId) {
    List<GroupMessageOrRequest> groupMsges = communicationDao.getGroupMessageByGroupId(groupId);
    List<GroupInvitation> rc = new ArrayList<>();
    for (GroupMessageOrRequest groupMOR : groupMsges) {
      if (CommunicationStatus.NEW.equals(groupMOR.getStatus())) {
        GroupInvitation invitation = new GroupInvitation();
        invitation.setRequestId(groupMOR.getId());
        List<Invitee> recipients = new ArrayList<>();
        for (CommunicationTarget target : groupMOR.getRecipients()) {
          if (target.getStatus().equals(CommunicationStatus.NEW)) {
            String email = target.getRecipient().getEmail();
            Long recipientId = target.getId();
            recipients.add(new Invitee(email, recipientId));
          }
        }
        invitation.setRecipients(recipients);
        rc.add(invitation);
      }
    }
    return rc;
  }

  @Override
  public void setRoleForUser(long groupId, Long userid, String roleStr, User subject) {
    RoleInGroup role = RoleInGroup.valueOf(roleStr);
    Group group = getGroup(groupId);
    Set<UserGroup> ugs = group.getUserGroups();
    User toChange = userDao.get(userid);
    if (!groupPermUtils.subjectCanAlterGroupRole(group, subject, toChange)) {
      throw new AuthorizationException(
          authMsgGenerator.getFailedMessage(
              subject,
              String.format(
                  "change role of %s user in '%s' group",
                  toChange.getUsername(), group.getDisplayName())));
    }

    for (UserGroup ug : ugs) {
      if (ug.getUser().getUsername().equals(toChange.getUsername())) {

        if (isPiDemotion(role, ug)) {
          if (isAttemptToDemoteOnlyPiInGroup(group, ug)) {
            throw new IllegalStateException(
                "Attempt to change the role of the only PI in the group!");
          }
          // if we're demoting a PI role, need to remove group members folders from the group.
          if (group.isLabGroup()) {
            removeMemberFoldersFromAdminsLabGroupsFolder(
                group.getMembers(), group, TransformerUtils.toSet(ug.getUser()));
            removePiGroupPermissions(group, ug);
          }
        } else if (isLabAdminDemotion(role, ug)) {
          removeLabAdminViewAllPermissions(group, ug);
          ug.setAdminViewDocsEnabled(false);
        }
        ug.setRoleInGroup(role, getPermissionsForRole(role, group));
        groupDao.save(group);
        ugDao.save(ug);
        permissnUtils.notifyUserOrGroupToRefreshCache(toChange);
        break;
      }
    }
  }

  private void removePiGroupPermissions(Group group, UserGroup ug) {
    Set<ConstraintBasedPermission> perms = permFactory.createDefaultPermissionsForGroupPI(group);
    perms.stream().forEach(cbp -> ug.removePermission(cbp));
  }

  private void removeLabAdminViewAllPermissions(Group g, UserGroup ug) {
    Set<User> labMembers = g.getAllNonPIMembers();
    labMembers.remove(ug.getUser());
    // remove ALL permissions for a single user
    Set<User> labAdmins = TransformerUtils.toSet(ug.getUser());
    removeUserFoldersAndLabAdminPermissions(labMembers, labAdmins, g);
  }

  private boolean isLabAdminDemotion(RoleInGroup role, UserGroup ug) {
    return ug.getRoleInGroup().equals(RoleInGroup.RS_LAB_ADMIN) && RoleInGroup.DEFAULT.equals(role);
  }

  private boolean isPiDemotion(RoleInGroup role, UserGroup ug) {
    return ug.getRoleInGroup().equals(RoleInGroup.PI) && !RoleInGroup.PI.equals(role);
  }

  // can't have a group with no PI!
  private boolean isAttemptToDemoteOnlyPiInGroup(Group g, UserGroup ug) {
    return g.getPiusers().size() == 1 && g.getPiusers().iterator().next().equals(ug.getUser());
  }

  @Override
  public Set<Group> listGroupsForUser() {
    String uname = (String) SecurityUtils.getSubject().getPrincipal();
    return userDao.getUserByUsername(uname).getGroups();
  }

  @Override
  public List<Group> listGroupsForOwner(User owner) {
    return groupDao.getForOwner(owner);
  }

  @Override
  public List<UserView> getCandidateMembersOfCollabGroup(Long id) {
    return groupDao.getCandidateMembersOfCollabGroup(id);
  }

  @Override
  public void removeLabGroupMembersFromCollabGroup(Long collabGroupId, User pi) {
    Group collabGroup = groupDao.get(collabGroupId);
    if (!permissnUtils.isPermitted(collabGroup, PermissionType.WRITE, pi)) {
      throw new AuthorizationException(
          String.format(
              "Attempt to edit group membership of group [%s] by %s",
              collabGroup.getDisplayName(), pi.getUsername()));
    }
    Set<User> allUsers = new HashSet<User>();
    Set<Group> gps = pi.getGroups();
    for (Group g : gps) {
      if (g.isLabGroup()) {
        allUsers.addAll(g.getMembers());
      }
    }

    Collection<User> toRemove = CollectionUtils.intersection(allUsers, collabGroup.getMembers());
    for (User removeme : toRemove) {
      removeUserFromGroup(removeme.getUsername(), collabGroupId, pi);
    }
    // delete group if this is the last collab group to be deleted
    if (collabGroup.isEmpty()) {
      removeGroup(collabGroup.getId(), pi);
    }
  }

  @Override
  public Group getGroupWithCommunities(Long groupId) {
    return groupDao.getGroupWithCommunities(groupId);
  }

  public User promoteUserToPi(User toPromote, User subject) {
    // we're ok to proceed. move user from existing group
    for (Group grp : toPromote.getGroups()) {
      if (grp.isLabGroup() && grp.isOnlyGroupPi(toPromote.getUsername())) {
        log.warn(
            "Attempting to create PI role for {} but is already a PI in group [{}]",
            toPromote.getFullName(),
            grp.getDisplayName());
        continue;
      } else if (grp.isLabGroup()) {
        removeUserFromGroup(toPromote.getUsername(), grp.getId(), subject);
      } else if (grp.isCollaborationGroup()) {
        // don't remove from collabGroup
        setRoleForUser(grp.getId(), toPromote.getId(), RoleInGroup.PI.name(), subject);
      }
    }
    // Update the object (User)
    toPromote = userDao.get(toPromote.getId());

    // Now update role
    toPromote.addRole(roleDao.getRoleByName(Constants.PI_ROLE));
    return userDao.save(toPromote);
  }

  @Override
  public boolean groupExists(String uniqueName) {
    return groupDao.getByUniqueName(uniqueName) != null;
  }

  @Override
  public List<Group> searchGroups(String term) {
    return groupDao.searchGroups(term);
  }

  public User authorizeLabAdminToViewAll(
      Long labAdminId, User subject, Long groupId, boolean enable) {
    Group group = getGroup(groupId);
    if (!group.isLabGroup()) {
      throw new UnsupportedOperationException(
          "Lab admin can only view documents of a lab group, "
              + "but this group is "
              + group.getGroupType());
    }
    if (!permissnUtils.isPermitted(group, PermissionType.WRITE, subject)
        || subject.hasRoleInGroup(group, RoleInGroup.RS_LAB_ADMIN)) {
      throw new AuthorizationException(
          "Only a PI in group, or an admin can authorise lab admin "
              + "but is ("
              + subject.getUsername()
              + ")");
    }
    User labAdmin = userDao.get(labAdminId);
    if (!(labAdmin.hasRoleInGroup(group, RoleInGroup.RS_LAB_ADMIN))) {
      throw new AuthorizationException(
          "Only a lab admin of this group can be authorised to view content, "
              + labAdmin.getUsername());
    }

    UserGroup labAdminUg = group.getUserGroupForUser(labAdmin);
    boolean permissionChanged = false;
    if (enable && !labAdminUg.isAdminViewDocsEnabled()) {
      Set<User> usersThatAdminCanView = group.getAllNonPIMembers();
      usersThatAdminCanView.remove(
          labAdmin); // admin can already see their own stuff, no need for another perm
      createRecordViewPermissionByOwner(labAdmin, usersThatAdminCanView);
      addMemberFoldersToAdminsLabGroupsFolder(
          usersThatAdminCanView, TransformerUtils.toSet(labAdmin));
      permissionChanged = true;
    } else if (!enable && labAdminUg.isAdminViewDocsEnabled()) {
      removeLabAdminViewAllPermissions(group, labAdminUg);
      permissionChanged = true;
    }

    if (permissionChanged) {
      labAdminUg.setAdminViewDocsEnabled(enable);
      ugDao.save(labAdminUg);
      labAdmin = userDao.save(labAdmin);
      permissnUtils.notifyUserOrGroupToRefreshCache(labAdmin);

      // update returned object immediately, so unit test can see it
      Optional<UserGroup> currentLabAdminUGToUpdate =
          labAdmin.getUserGroups().stream()
              .filter(ug -> labAdminUg.getId().equals(ug.getId()))
              .findFirst();
      // TSK-25 there must be a case where ug is not there, let's ensure we don't get an exception
      if (currentLabAdminUGToUpdate.isPresent()) {
        currentLabAdminUGToUpdate.get().setAdminViewDocsEnabled(enable);
      }
    }
    if (!permissionChanged) {
      log.info(
          "Attempting to set the admin view all to the same value for {} in group {}",
          labAdmin.getUsername(),
          group.getDisplayName());
    }
    return labAdmin;
  }

  @Override
  public void authorizePIToEditAll(Long groupId, User subject, boolean canPIEditAll) {
    Group group = getGroupWithCommunities(groupId);
    if (!group.isLabGroup()) {
      throw new UnsupportedOperationException(
          "PI can only edit all documents in a lab group, but this group is "
              + group.getGroupType());
    }

    if (!group.getPiusers().contains(subject)) {
      throw new AuthorizationException("Only a PI of this group can change this setting");
    }
    if (!groupPermUtils.piCanEditAllWorkInLabGroup(group) && canPIEditAll) {
      throw new AuthorizationException(
          "System admin or community admin has not allowed  this setting to be changed");
    }
    groupPermUtils.setReadOrEditAllPermissionsForPi(group, subject, canPIEditAll);
  }

  @Override
  public void authorizeAllPIsToEditAll(Long groupId, User subject, boolean canPIEditAll) {
    Group group = getGroupWithCommunities(groupId);

    for (User groupPI : group.getPiusers()) {
      if (!groupPermUtils.subjectCanAlterGroupRole(group, subject, groupPI)) {
        throw new AuthorizationException(
            authMsgGenerator.getFailedMessage(
                subject,
                String.format(
                    " change %s 's edit all work permission in group %s ",
                    groupPI.getUsername(), group.getDisplayName())));
      }
      authorizePIToEditAll(groupId, groupPI, canPIEditAll);
    }
  }

  private void createRecordViewPermissionByOwner(User admin, Set<User> usersThatAdminCanView) {
    Set<String> unames =
        usersThatAdminCanView.stream()
            .map(new ObjectToStringPropertyTransformer<User>("username"))
            .collect(Collectors.toSet());
    PropertyConstraint pc = new PropertyConstraint("owner", StringUtils.join(unames, ","));
    log.info("LabAdmin can view {} ({}) chars", pc, pc.getValue().length());
    ConstraintBasedPermission cbp =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    cbp.addPropertyConstraint(pc);
    admin.addPermission(cbp);
  }

  @Override
  public List<Group> getGroups(Collection<Long> groupIds) {
    return groupDao.getGroups(groupIds);
  }

  @Override
  public Group hideGroupProfile(Boolean hideProfile, Group group, User subject) {
    if (!subject.hasRoleInGroup(group, RoleInGroup.PI)
        && !subject.hasRoleInGroup(group, RoleInGroup.RS_LAB_ADMIN)) {
      throw new AuthorizationException(
          "Only a PI or Lab Admin can change group visibility, "
              + "but is ("
              + subject.getUsername()
              + ")");
    }
    group.setPrivateProfile(hideProfile);
    return saveGroup(group, subject);
  }

  @Override
  public Group setNewPi(long groupId, Long newPiId, User admin) {
    Group grp = groupDao.get(groupId);

    User newPi = userDao.get(newPiId);
    User currPi = grp.getPiusers().iterator().next();

    BindingResult result =
        inputValidator.validate(new SwapPiCommand(currPi, newPi, grp), new SwapPiValidator());
    if (result.hasErrors()) {
      ErrorList el = new ErrorList();
      inputValidator.populateErrorList(result, el);
      throw new IllegalArgumentException(el.getAllErrorMessagesAsStringsSeparatedBy(";"));
    }

    PiChangeContext ctxt = new PiChangeContext(grp.getUserGroupForUser(currPi).isPiCanEditWork());
    // add new PI first
    piChangeHandler.beforePiChanged(currPi, grp, newPi, ctxt);
    doChangePi(groupId, newPiId, admin, currPi);
    grp.setOwner(newPi);
    groupDao.save(grp);
    piChangeHandler.afterPiChanged(currPi, grp, newPi, ctxt);
    return groupDao.get(groupId);
  }

  private void doChangePi(long groupId, Long newPiId, User admin, User currPi) {
    setRoleForUser(groupId, newPiId, RoleInGroup.PI.name(), admin);
    // then remove old one so there is always a PI
    setRoleForUser(groupId, currPi.getId(), RoleInGroup.DEFAULT.name(), admin);
  }

  @Override
  public List<UserGroup> findByUserId(Long userId) {
    return ugDao.findByUserId(userId);
  }

  @Override
  public List<GroupMembershipEvent> getGroupEventsForUser(User sessionUser, User toRetrieve) {
    return groupMembershipEventDao.getGroupEventsForUser(toRetrieve);
  }

  @Override
  public List<GroupMembershipEvent> getGroupEventsForGroup(User sessionUser, Group toRetrieve) {
    return groupMembershipEventDao.getGroupEventsForGroup(toRetrieve);
  }

  @Override
  public Group enableAutoshareForUser(User user, Long groupId) {
    Group group = groupDao.get(groupId);

    if (!group.isLabGroup()) {
      log.warn(
          "Autosharing is only possible in labGroup but group {} has type {}",
          groupId,
          group.getGroupType());
      return group;
    }
    validateUserInGroup(user, group);

    UserGroup uGroup = group.getUserGroupForUser(user);
    uGroup.setAutoshareEnabled(true);
    ugDao.save(uGroup);

    groupMembershipEventDao.save(
        new GroupMembershipEvent(user, group, GroupEventType.ENABLED_AUTOSHARING));

    return groupDao.save(group);
  }

  @Override
  public Folder createAutoshareFolder(User user, Group group) {
    return createAutoshareFolder(user, group, null);
  }

  @Override
  public Folder createAutoshareFolder(User user, Group group, String folderName) {
    validateUserInGroup(user, group);

    if (folderName == null) {
      folderName = user.getUsername() + "-autoshared";
    }

    UserGroup ug = group.getUserGroupForUser(user);
    if (ug.getAutoShareFolder() != null) {
      log.info("autoshare folder is already set - id = " + ug.getAutoShareFolder().getId());
      return folderDao.get(ug.getAutoShareFolder().getId());
    } else {
      log.info(
          "autoshare folder is not set for {} in group {}, creating...",
          user.getUsername(),
          group.getId());
      return doCreateAutoshareFolder(user, group, ug, folderName);
    }
  }

  @Override
  public Group getGroupByCommunalGroupFolderId(Long communalGroupFolderId) {
    return groupDao.getByCommunalGroupFolderId(communalGroupFolderId);
  }

  private Folder doCreateAutoshareFolder(User user, Group group, UserGroup ug, String folderName) {
    Folder groupFolder = folderMgr.getFolder(group.getCommunalGroupFolderId(), user);
    Folder autoshareFolder = folderMgr.createNewFolder(groupFolder.getId(), folderName, user);
    ug.setAutoShareFolder(autoshareFolder);
    ugDao.save(ug);

    return autoshareFolder;
  }

  private void validateUserInGroup(User user, Group group) {
    if (!user.hasGroup(group)) {
      throw new IllegalArgumentException(
          String.format("User %s is not in group %s", user.getUsername(), group.getDisplayName()));
    }
  }

  @Override
  public Group disableAutoshareForUser(User user, Long groupId) {
    Group group = groupDao.get(groupId);

    validateUserInGroup(user, group);

    UserGroup ug = group.getUserGroupForUser(user);
    ug.setAutoshareEnabled(false);
    ug.setAutoShareFolder(null);
    ugDao.save(ug);

    groupMembershipEventDao.save(
        new GroupMembershipEvent(user, group, GroupEventType.DISABLED_AUTOSHARING));

    return groupDao.save(group);
  }
}
