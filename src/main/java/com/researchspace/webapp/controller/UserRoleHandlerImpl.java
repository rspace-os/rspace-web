package com.researchspace.webapp.controller;

import com.researchspace.Constants;
import com.researchspace.auth.UserPermissionUtils;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserRoleChangeCmnd;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.EmailContent;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.ListFormatUtils;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.UserRoleHandler;
import com.researchspace.service.impl.EmailContentGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UserRoleHandlerImpl implements UserRoleHandler {

  private @Autowired IGroupCreationStrategy grpStrategy;
  private @Autowired UserPermissionUtils userPermissionUtils;
  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired GroupManager groupManager;
  private @Autowired UserManager userManager;
  private @Autowired IPropertyHolder properties;
  private @Autowired IContentInitializer initializer;
  private @Autowired RoleManager roleMgr;
  private @Autowired EmailContentGenerator emailContentGenerator;
  private @Autowired MessageSourceUtils messages;

  @Autowired
  @Qualifier("emailBroadcast")
  private EmailBroadcast emailer;

  @Override
  public Group promoteUserToPiWithGroup(User admin, User newPI, UserRoleChangeCmnd commd) {
    assertAuthorizationAndInitUser(admin, newPI, "errors.authorization.failure.promoteUserToPi");
    // we're ok to proceed. move user from existing group
    newPI = groupManager.promoteUserToPi(newPI, admin);
    // now create new LabGroup for new PI
    Group newLabGroup = grpStrategy.createAndSaveGroup(newPI, admin, GroupType.LAB_GROUP, newPI);
    notifyByEmailOfPIpromotion(newPI, newLabGroup, admin);
    return newLabGroup;
  }

  @Override
  public User setNewlySignedUpUserAsPi(User newPI) {
    Validate.isTrue(
        properties.isPicreateGroupOnSignupEnabled(),
        String.format(
            "This method requires deployment property '%s' to be enabled",
            "picreateGroupOnSignup.enabled"));
    Group newLabGroup = null;
    if (newPI.isPicreateGroupOnSignup()) {
      newPI = initializeUserIfNew(newPI);
      // we're ok to proceed.
      newPI = doGrantGlobalPiRoleToUser(newPI);
      // now create new LabGroup for new PI
      newLabGroup = grpStrategy.createAndSaveGroup(newPI, newPI, GroupType.LAB_GROUP, newPI);
      log.info("Group {} created for user {}", newLabGroup.getDisplayName(), newPI.getUsername());
    }
    return newPI;
  }

  public User grantGlobalPiRoleToUser(User admin, User newPi) {
    assertAuthorizationAndInitUser(admin, newPi, "errors.authorization.failure.grantGlobalPiRole");
    return doGrantGlobalPiRoleToUser(newPi);
  }

  @Override
  public User doGrantGlobalPiRoleToUser(User newPi) {
    if (newPi.hasRole(Role.PI_ROLE)) {
      log.warn("User [{}] already has PI role, no action needed", newPi.getUsername());
      return newPi;
    }
    newPi.addRole(roleMgr.getRole(Constants.PI_ROLE));
    permissionUtils.notifyUserOrGroupToRefreshCache(newPi);
    return userManager.save(newPi);
  }

  private void notifyByEmailOfPIpromotion(User newPI, Group newLabGroup, User sysadmin) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("newPI", newPI);
    velocityModel.put("systemUser", sysadmin);
    velocityModel.put("newLabGroup", newLabGroup);
    velocityModel.put("baseURL", properties.getServerUrl());

    sentHtmlEmailLogAnyException("email.admin.promoteToPiComplete.subject", newPI, velocityModel);
  }

  @Override
  public User revokeGlobalPiRoleFromUser(User admin, User piToDemote) {
    assertAuthorizationAndInitUser(
        admin, piToDemote, "errors.authorization.failure.revokeGlobalPiRole");
    return doRevokeGlobalPiRoleFromUser(piToDemote);
  }

  @Override
  public User doRevokeGlobalPiRoleFromUser(User piToDemote) {
    if (!piToDemote.hasRole(Role.PI_ROLE)) {
      log.warn("User [{}] is not  a PI role, no action needed", piToDemote.getUsername());
      return piToDemote;
    }
    List<String> grpsAsPi =
        piToDemote.getGroups().stream()
            .filter(grp -> piToDemote.hasRoleInGroup(grp, RoleInGroup.PI))
            .map(g -> g.getDisplayName() + " [" + g.getId() + "]")
            .collect(Collectors.toList());
    if (!grpsAsPi.isEmpty()) {
      throw new IllegalStateException(
          messages.getMessage(
              "groups.edit.errors.piCannotBeDemoted",
              new Object[] {piToDemote.getUsername(), ListFormatUtils.formatList(grpsAsPi)}));
    }

    piToDemote.removeRole(roleMgr.getRole(Constants.PI_ROLE));
    // ensure always has user role
    if (!piToDemote.hasRole(Role.USER_ROLE)) {
      piToDemote.addRole(roleMgr.getRole(Constants.USER_ROLE));
    }
    permissionUtils.notifyUserOrGroupToRefreshCache(piToDemote);
    return userManager.save(piToDemote);
  }

  private void sentHtmlEmailLogAnyException(
      String subjectKey, User user, Map<String, Object> velocityModel) {
    EmailContent content =
        emailContentGenerator.render(subjectKey, "promoteToPIComplete.vm", velocityModel);
    emailer.sendEmail(content, List.of(user.getEmail()), null);
  }

  private void assertAuthorizationAndInitUser(User admin, User newPI, String authFailureMsg) {
    userPermissionUtils.assertHasPermissionsOnTargetUser(admin, newPI, authFailureMsg);
    initializeUserIfNew(newPI);
  }

  private User initializeUserIfNew(User newPI) {
    if (!newPI.isContentInitialized()) {
      log.info("Initialising content for user {}", newPI.getUsername());
      initializer.init(newPI.getId());
    }
    return userManager.getUserByUsername(newPI.getUsername(), true);
  }
}
