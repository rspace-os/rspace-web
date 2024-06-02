package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.webapp.filter.RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD;
import static org.apache.commons.lang.StringUtils.isBlank;

import com.researchspace.Constants;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.ProductType;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.GroupManager;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.SysadminUserCreationHandler;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.SysAdminCreateUser;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;

public class SysadminUserCreationHandlerImpl implements SysadminUserCreationHandler {

  private @Autowired IPropertyHolder properties;
  private @Autowired IGroupCreationStrategy grpStrategy;
  private @Autowired UserValidator userValidator;
  private @Autowired RemoteUserRetrievalPolicy remoteUserRetrievalPolicy;
  private @Autowired CommunityServiceManager communityService;
  private @Autowired AuditTrailService auditService;
  private @Autowired UserManager userManager;
  private @Autowired GroupManager groupManager;
  private @Autowired IContentInitializer initializer;
  private @Autowired MessageSource messageSource;
  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;

  @Autowired
  @Qualifier("emailBroadcast")
  private EmailBroadcast emailer;

  @Override
  public AjaxReturnObject<User> createUser(SysAdminCreateUser userForm, User subject) {
    ErrorList errorList = new ErrorList();
    String role = userForm.getRole();
    /* General validation: Checking that no options are blank */
    if (passwordRequiredButBlank(
            userForm.getPassword(), userForm.getPasswordConfirmation(), userForm.isLdapAuthChoice())
        || affiliationRequiredButBlank(userForm.getPassword())) {
      errorList.addErrorMsg(getText("errors.allfields.required"));
    }

    /* Username validation */
    String usernameValidationMsg = userValidator.validateUsername(userForm.getUsername());
    if (!UserValidator.FIELD_OK.equals(usernameValidationMsg)) {
      errorList.addErrorMsg(usernameValidationMsg);
      return new AjaxReturnObject<User>(null, errorList);
    }

    /* Password validation */
    if ((!properties.isSSO() && !userForm.isLdapAuthChoice()) || userForm.isSsoBackdoorAccount()) {
      String passwordValidationMsg =
          userValidator.validatePasswords(
              userForm.getPassword(), userForm.getPasswordConfirmation(), userForm.getUsername());
      if (!UserValidator.FIELD_OK.equals(passwordValidationMsg)) {
        errorList.addErrorMsg(passwordValidationMsg);
        return new AjaxReturnObject<User>(null, errorList);
      }
    }

    /* Role validation */
    if (!Role.isRoleStringIdentifiable(role)) {
      errorList.addErrorMsg(
          getText(
              "errors.invalid.roleidentifier",
              new Object[] {role, StringUtils.join(Role.getValidRoles())}));
      return new AjaxReturnObject<User>(null, errorList);
    }

    boolean isSubjectSysAdmin = subject.hasRole(Role.SYSTEM_ROLE);
    boolean isSubjectRSpaceAdmin = subject.hasRole(Role.ADMIN_ROLE);

    boolean isUserRole = role.equalsIgnoreCase(Constants.USER_ROLE);
    boolean isPIRole = role.equalsIgnoreCase(Constants.PI_ROLE);
    boolean isRSpaceAdminRole = role.equalsIgnoreCase(Constants.ADMIN_ROLE);
    boolean isSysAdminRole = role.equalsIgnoreCase(Constants.SYSADMIN_ROLE);

    User newUser = null;
    if (isUserRole && (isSubjectSysAdmin || isSubjectRSpaceAdmin)) {

      newUser = attemptUserSave(userForm, subject, errorList, newUser);
      if (errorList.hasErrorMessages()) {
        return new AjaxReturnObject<User>(null, errorList);
      }
      if (userForm.getLabGroupId() != null) {
        groupManager.addUserToGroup(
            userForm.getUsername(), userForm.getLabGroupId(), RoleInGroup.DEFAULT);
      } else {
        errorList.addErrorMsg(getText("errors.user.notingroup"));
      }

    } else if (isPIRole && (isSubjectSysAdmin || isSubjectRSpaceAdmin)) {

      if (userForm.isCreateGroupForPiUser()) {
        if (isValidGroupCreationConfig(userForm)) {
          newUser = attemptUserSave(userForm, subject, errorList, newUser);
          if (errorList.hasErrorMessages()) {
            return new AjaxReturnObject<User>(null, errorList);
          }
          // Creating new LabGroup for new PI
          Group newLabGroup =
              grpStrategy.createAndSaveGroup(
                  userForm.getNewLabGroupName(), newUser, subject, GroupType.LAB_GROUP, newUser);
          communityService.addGroupToCommunity(
              newLabGroup.getId(), userForm.getCommunityId(), subject);
        } else {
          errorList.addErrorMsg(getText("errors.missinggroup.name"));
          return new AjaxReturnObject<User>(null, errorList);
        }
      } else {
        newUser = attemptUserSave(userForm, subject, errorList, newUser);
        if (errorList.hasErrorMessages()) {
          return new AjaxReturnObject<User>(null, errorList);
        }
      }

    } else if (isRSpaceAdminRole && (isSubjectSysAdmin || isSubjectRSpaceAdmin)) {

      newUser = attemptUserSave(userForm, subject, errorList, newUser);
      if (errorList.hasErrorMessages()) {
        return new AjaxReturnObject<User>(null, errorList);
      }

      if (userForm.isCommunitySet()) {
        // Adding new RSpaceAdmin as Admin (Community).
        Community community = communityService.get(userForm.getCommunityId());
        Community comm =
            communityService.addAdminsToCommunity(new Long[] {newUser.getId()}, community.getId());
        communityService.save(comm);
      } else {
        errorList.addErrorMsg(getText("errors.adminnotincommunity"));
      }

    } else if (isSysAdminRole) {
      if (!isSubjectSysAdmin) {
        throw new AuthorizationException("Only a sysadmin can create another sysadmin!");
      }
      newUser = attemptUserSave(userForm, subject, errorList, newUser);
      if (errorList.hasErrorMessages()) {
        return new AjaxReturnObject<User>(null, errorList);
      }
    }
    auditService.notify(new GenericEvent(subject, newUser, AuditAction.CREATE));
    return new AjaxReturnObject<User>(newUser, errorList);
  }

  private boolean isValidGroupCreationConfig(SysAdminCreateUser userForm) {
    return (userForm.getCommunityId() != null) && !isBlank(userForm.getNewLabGroupName());
  }

  private String getText(String string, Object[] args) {
    return messageSource.getMessage(string, new Object[] {}, Locale.getDefault());
  }

  private String getText(String string) {
    return getText(string, new Object[] {});
  }

  private User attemptUserSave(
      SysAdminCreateUser userForm, User subject, ErrorList error, User newUser) {
    try {
      newUser = createNewUserAccountAndNotify(userForm, subject);
    } catch (UserExistsException e) {
      error.addErrorMsg(
          getText(
              "errors.existing.user", new Object[] {userForm.getUsername(), userForm.getEmail()}));
    }
    return newUser;
  }

  private boolean passwordRequiredButBlank(
      String password, String passwordConfirmation, boolean ldapAuth) {
    return properties.isStandalone()
        && !ldapAuth
        && (isBlank(password) || isBlank(passwordConfirmation));
  }

  private boolean affiliationRequiredButBlank(String affiliation) {
    return properties.isCloud() && isBlank(affiliation);
  }

  /**
   * Method to create a user account into the application (database).
   *
   * @param userForm SysAdminCreateUser
   */
  private User createNewUserAccountAndNotify(SysAdminCreateUser userForm, User adminUserInSession)
      throws UserExistsException, IllegalAddChildOperation {

    User newUser = new User();
    newUser.setFirstName(userForm.getFirstName());
    newUser.setLastName(userForm.getLastName());
    newUser.setUsername(userForm.getUsername());
    newUser.setEmail(userForm.getEmail());
    newUser.setRole(userForm.getRole());
    if (userForm.isLdapAuthChoice()) {
      newUser.setSignupSource(SignupSource.LDAP);
      newUser.setPassword(SSO_DUMMY_PASSWORD);
      newUser.setConfirmPassword(SSO_DUMMY_PASSWORD);
    } else {
      newUser.setPassword(userForm.getPassword());
    }
    setAffiliation(userForm.getAffiliation(), newUser);

    if (properties.isSSO() && !userForm.isSsoBackdoorAccount()) {
      String pwd = remoteUserRetrievalPolicy.getPassword();
      newUser.setPassword(pwd);
      newUser.setConfirmPassword(pwd);
    }
    if (userForm.isSsoBackdoorAccount()) {
      newUser.setSignupSource(SignupSource.SSO_BACKDOOR);
    }

    // get refreshed object with ID
    newUser = createNewUserAccount(newUser);
    notifyByEmailOfNewAccountCreation(newUser, adminUserInSession, userForm.getRole());
    return newUser;
  }

  private void setAffiliation(String affiliation, User newUser) {
    if (properties.isCloud()) {
      newUser.setAffiliation(affiliation, ProductType.COMMUNITY);
    }
  }

  private void notifyByEmailOfNewAccountCreation(User newUser, User adminUser, String role) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("newUser", newUser);
    velocityModel.put("newUserRole", role.split("_")[1]);
    velocityModel.put("adminUser", adminUser);
    velocityModel.put("htmlPrefix", properties.getServerUrl());
    EmailContent content =
        strictEmailContentGenerator.generatePlainTextAndHtmlContent(
            "newUserAccountComplete.vm", velocityModel);
    emailer.sendHtmlEmail("RSpace account created", content, toList(newUser.getEmail()), null);
  }

  private User createNewUserAccount(User newUser) throws UserExistsException {
    userManager.saveNewUser(newUser);
    if (!newUser.isContentInitialized()) {
      initializer.init(newUser.getId());
    }
    return userManager.getUserByUsername(newUser.getUsername());
  }
}
