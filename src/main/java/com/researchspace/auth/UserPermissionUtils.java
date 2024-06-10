package com.researchspace.auth;

import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IUserPermissionUtils;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.UserManager;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserPermissionUtils implements IUserPermissionUtils {

  private @Autowired UserManager userManager;
  private @Autowired OperationFailedMessageGenerator authGenerator;

  public void assertHasPermissionsOnTargetUser(User admin, User newPI, String msgOnFailure) {
    boolean ok = isTargetUserValidForSubjectRole(admin, newPI.getUsername());
    if (!ok) {
      throw new AuthorizationException(authGenerator.getFailedMessage(admin, msgOnFailure));
    }
  }

  public boolean isTargetUserValidForSubjectRole(User subject, String targetUser) {
    // pis and users can't operate-as
    if (!subject.hasAdminRole()) {
      return false;
    }
    return subject.hasRole(Role.SYSTEM_ROLE)
        || isCommunityAdminAndTargetUserInCommunity(subject, targetUser);
  }

  private boolean isCommunityAdminAndTargetUserInCommunity(User subject, String targetUsername) {
    if (!subject.hasRole(Role.ADMIN_ROLE)) {
      return false;
    }
    // subject is now known to be a community admin.
    User target = userManager.getUserByUsername(targetUsername);
    if (target.hasAdminRole()) {
      return false; // a CA can't escalate to operate as a Sysadmin or other CA, ever. RSPAC-2529
    }
    // target is a regular PI or user. Subject can operate-as if PI or user is in subject's
    // community
    else if ((target.hasRole(Role.PI_ROLE) || target.hasRole(Role.USER_ROLE))) {
      return userManager.isUserInAdminsCommunity(subject, targetUsername);
    } else {
      return false; // default fallback, shouldn't be called unless we add new roles
    }
  }

}
