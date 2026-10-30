package com.researchspace.auth;

import com.researchspace.dao.CommunityDao;
import com.researchspace.dao.UserDao;
import com.researchspace.dao.UserGroupDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserGroup;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.Preference;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.Permission;
import org.springframework.beans.factory.annotation.Autowired;

public class GroupPermissionUtils implements IGroupPermissionUtils {

  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired UserGroupDao ugDao;
  private @Autowired UserDao userDao;
  private @Autowired CommunityDao communityDao;
  private @Autowired IPropertyHolder properties;
  private @Autowired MessageSourceUtils messages;
  private @Autowired SystemPropertyPermissionManager systemPropertyMgrPermUtils;

  @Override
  public boolean subjectCanAlterGroupRole(Group group, User subject, User toChange) {
    if (group.isCollaborationGroup()) {
      return subject.hasRoleInGroup(group, RoleInGroup.PI)
          || subject.hasRole(Role.SYSTEM_ROLE)
          || (subject.hasRole(Role.ADMIN_ROLE) && hasLabInAdminsCommunity(group, subject));
    }
    boolean canAlter = false;
    if (permissionUtils.isPermitted(group, PermissionType.WRITE, subject)) {
      if (subjectIsPIOrAdmin(group, subject)) {
        canAlter = true;
      }
    }
    if (group.isProjectGroup() && subject.hasRoleInGroup(group, RoleInGroup.GROUP_OWNER)) {
      canAlter = true;
    }
    return canAlter;
  }

  private boolean subjectIsPIOrAdmin(Group group, User subject) {
    return subject.hasRoleInGroup(group, RoleInGroup.PI) || subject.hasAdminRole();
  }

  // a collab group's labs are those its PIs lead; Group#getMemberGroupsForCollabGroup is unreliable
  // (RSPAC-2500)
  private boolean hasLabInAdminsCommunity(Group collabGroup, User admin) {
    Set<Long> adminCommunityIds =
        communityDao.listCommunitiesForAdmin(admin.getId()).stream()
            .map(Community::getId)
            .collect(Collectors.toSet());
    for (User pi : collabGroup.getPiusers()) {
      for (Group lab : pi.getGroups()) {
        if (lab.isLabGroup()
            && pi.hasRoleInGroup(lab, RoleInGroup.PI)
            && adminCommunityIds.contains(lab.getCommunityId())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public UserGroup setReadOrEditAllPermissionsForPi(
      Group group, User subject, boolean canPIEditAll) {
    UserGroup userGroup = group.getUserGroupForUser(subject);
    subject.getUserGroups().remove(userGroup);
    if (canPIEditAll != userGroup.isPiCanEditWork()) {
      if (canPIEditAll) {
        // Add WRITE permission
        for (Permission permission : userGroup.getPermissions()) {
          ConstraintBasedPermission cbp = (ConstraintBasedPermission) permission;
          if (isRecordDomainWithGroupConstraint(group, cbp)) {
            userGroup.removePermission(cbp);
            cbp.addPermissionType(PermissionType.WRITE);
            userGroup.addPermission(cbp);
            userGroup.setPiCanEditWork(true);
            break;
          }
        }
      } else {
        // Remove WRITE permission
        for (Permission permission : userGroup.getPermissions()) {
          ConstraintBasedPermission cbp = (ConstraintBasedPermission) permission;
          if (isRecordDomainWithGroupConstraint(group, cbp)
              && cbp.getActions().contains(PermissionType.WRITE)) {
            userGroup.removePermission(cbp);
            cbp.removePermissionType(PermissionType.WRITE);
            userGroup.addPermission(cbp);
            userGroup.setPiCanEditWork(false);
            break;
          }
        }
      }
      subject.getUserGroups().add(userGroup);
      userGroup = ugDao.save(userGroup);
      permissionUtils.notifyUserOrGroupToRefreshCache(subject);
    }
    return userGroup;
  }

  private boolean isRecordDomainWithGroupConstraint(Group group, ConstraintBasedPermission cbp) {
    return cbp.getDomain().equals(PermissionDomain.RECORD)
        && cbp.getGroupConstraint() != null
        && cbp.getGroupConstraint().getGroupName().equals(group.getUniqueName());
  }

  @Override
  public boolean userCanExportGroup(User exporter, Group group) {
    return exporter.hasRole(Role.SYSTEM_ROLE)
        || group.getPiusers().contains(exporter)
        || group.getLabAdminsWithViewAllPermission().contains(exporter);
  }

  @Override
  public boolean piCanEditAllWorkInLabGroup(Group group) {
    return systemPropertyMgrPermUtils.isPropertyAllowed(
        group, Preference.PI_CAN_EDIT_ALL_WORK_IN_LABGROUP.name());
  }

  public User assertLeaveGroupPermissions(String leaveCandidateUsername, User subject, Group grp) {
    // RSPAC-1662 lets users self-remove
    if (subject.getUsername().equals(leaveCandidateUsername)
        && (properties.isCloud() || grp.isProjectGroup())
        && !grp.isOnlyGroupPi(leaveCandidateUsername)) {
      return loadUser(leaveCandidateUsername);
    }
    // this must come after RSPAC-1662 test above
    if (!permissionUtils.isPermitted(grp, PermissionType.WRITE, subject)) {
      throwAuthException(subject, "errors.authorization.failure.removeUserFromGroup");
    }
    User userToRemove = loadUser(leaveCandidateUsername);
    if (userToRemove.hasRoleInGroup(grp, RoleInGroup.PI)
        && GroupType.LAB_GROUP.equals(grp.getGroupType())
        && !(subject.hasRoleInGroup(grp, RoleInGroup.PI) || subject.hasAdminRole())) {
      throwAuthException(subject, "errors.authorization.failure.removePiFromGroup");
    }
    return userToRemove;
  }

  private User loadUser(String leaveCandidateUsername) {
    return userDao.getUserByUsername(leaveCandidateUsername);
  }

  private void throwAuthException(User subject, String messageKey) {
    throw new AuthorizationException(
        messages.getMessage(messageKey, new Object[] {subject.getUsername()}));
  }
}
