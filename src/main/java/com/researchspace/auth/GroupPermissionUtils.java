package com.researchspace.auth;

import com.researchspace.dao.UserDao;
import com.researchspace.dao.UserGroupDao;
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
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.SystemPropertyPermissionManager;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.Permission;
import org.springframework.beans.factory.annotation.Autowired;

public class GroupPermissionUtils implements IGroupPermissionUtils {

  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired UserGroupDao ugDao;
  private @Autowired UserDao userDao;
  private @Autowired IPropertyHolder properties;
  private @Autowired OperationFailedMessageGenerator authMsgGenerator;
  private @Autowired SystemPropertyPermissionManager systemPropertyMgrPermUtils;

  @Override
  public boolean subjectCanAlterGroupRole(Group group, User subject, User toChange) {
    boolean canAlter = false;
    if (permissionUtils.isPermitted(group, PermissionType.WRITE, subject)) {
      if (subjectIsPIOrAdmin(group, subject)) {
        canAlter = true;
      }
    }
    // if a user who is already in a collab group is creating his own lab group,
    // it's OK to promote him to PI in collab group
    // note: this is maybe too loose, there should be constraints on collab group as well.
    if (group.isCollaborationGroup()) {
      canAlter = true;
    }
    if (group.isProjectGroup() && subject.hasRoleInGroup(group, RoleInGroup.GROUP_OWNER)) {
      canAlter = true;
    }
    return canAlter;
  }

  private boolean subjectIsPIOrAdmin(Group group, User subject) {
    return subject.hasRoleInGroup(group, RoleInGroup.PI) || subject.hasAdminRole();
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
      throwAuthException(subject, "remove user from group");
    }
    User userToRemove = loadUser(leaveCandidateUsername);
    if (userToRemove.hasRoleInGroup(grp, RoleInGroup.PI)
        && GroupType.LAB_GROUP.equals(grp.getGroupType())
        && !(subject.hasRoleInGroup(grp, RoleInGroup.PI) || subject.hasAdminRole())) {
      throwAuthException(
          subject,
          "remove PI from group. " + "Only A PI or an admin can remove another PI from group.");
    }
    return userToRemove;
  }

  private User loadUser(String leaveCandidateUsername) {
    return userDao.getUserByUserName(leaveCandidateUsername);
  }

  private void throwAuthException(User subject, String msg) {
    throw new AuthorizationException(authMsgGenerator.getFailedMessage(subject, msg));
  }
}
