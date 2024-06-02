package com.researchspace.webapp.controller;

import static com.researchspace.session.SessionAttributeUtils.BATCH_REGISTRATION_PROGRESS;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static org.apache.commons.lang.StringUtils.isBlank;

import com.axiope.userimport.IPostUserCreationSetUp;
import com.axiope.userimport.UserImportResult;
import com.axiope.userimport.UserListGenerator;
import com.researchspace.Constants;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.core.util.progress.ProgressMonitorImpl;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.Role;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.dto.CommunityPublicInfo;
import com.researchspace.model.dto.GroupPublicInfo;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.model.dto.UserRegistrationInfo;
import com.researchspace.model.dtos.CommunityValidator;
import com.researchspace.model.dtos.GroupValidator;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserExistsException;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

@Controller("sysAdminUserRegistrationController")
@RequestMapping("/system/userRegistration")
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
public class SysAdminUserRegistrationController extends BaseController {

  private @Autowired PermissionFactory permFac;
  private @Autowired UserListGenerator userListGenerator;
  private @Autowired UserValidator userValidator;
  private @Autowired GroupValidator groupValidator;
  private @Autowired CommunityValidator communityValidator;
  private @Autowired CommunityServiceManager communityService;
  private @Autowired IGroupCreationStrategy grpStrategy;
  private @Autowired IContentInitializer initializer;
  private @Autowired UserLdapRepo userLdapRepo;
  private @Autowired RemoteUserRetrievalPolicy remoteUserPolicy;
  private @Autowired UserEnablementUtils userEnablementUtils;

  private IPostUserCreationSetUp postUserCreationSetup;

  @Autowired
  @Qualifier("postUserCreate")
  public void setPostUserCreationSetup(IPostUserCreationSetUp postUserCreationSetup) {
    this.postUserCreationSetup = postUserCreationSetup;
  }

  /**
   * Batch user registration method that reads the csv file and returns parsed users and groups.
   *
   * @param xfile the csv file
   * @returns UserImportResult containing users, groups and ErrorList with parse errors
   * @throws IOException
   */
  @PostMapping("/csvUpload")
  @ResponseBody
  public UserImportResult batchUploadParseCsvFile(MultipartFile xfile) throws IOException {
    return getImportResultsFromCSVInput(xfile.getInputStream());
  }

  /**
   * Batch user registration method that parses input String and returns parsed users and groups.
   *
   * @param input string containing users/groups in csv format
   * @returns UserImportResult containing users, groups and ErrorList with parse errors
   * @throws IOException
   */
  @PostMapping("/parseInputString")
  @IgnoreInLoggingInterceptor(ignoreAllRequestParams = true)
  @ResponseBody
  public UserImportResult batchParseStringInput(
      @RequestParam("usersAndGroupsCsvFormat") String input) throws IOException {
    return getImportResultsFromCSVInput(IOUtils.toInputStream(input, "UTF-8"));
  }

  private UserImportResult getImportResultsFromCSVInput(InputStream inputStream) {
    UserImportResult usersToSignup = userListGenerator.getUsersToSignup(inputStream);
    if (TRUE.toString().equalsIgnoreCase(properties.getLdapEnabled())) {
      if (usersToSignup.getParsedUsers() != null) {
        for (UserRegistrationInfo user : usersToSignup.getParsedUsers()) {
          fetchLdapDetailsAndApplyToEmptyUserInfoFields(user);
        }
      }
    }
    return usersToSignup;
  }

  private void fetchLdapDetailsAndApplyToEmptyUserInfoFields(UserRegistrationInfo userInfo) {
    if (!isBlank(userInfo.getFirstName())
        && !isBlank(userInfo.getLastName())
        && !isBlank(userInfo.getEmail())) {
      return; // all details already provided, no need to query ldap
    }

    UserPublicInfo ldapDetails = null;
    try {
      User ldapUser = userLdapRepo.findUserByUsername(userInfo.getUsername());
      if (ldapUser != null) {
        ldapDetails = ldapUser.toPublicInfo();
      }
    } catch (RuntimeException re) {
      log.warn("exception when fetching ldap details for user " + userInfo.getUsername(), re);
    }
    if (ldapDetails == null) {
      return; // no data to apply
    }

    if (isBlank(userInfo.getFirstName()) && !isBlank(ldapDetails.getFirstName())) {
      userInfo.setFirstName(ldapDetails.getFirstName());
    }
    if (isBlank(userInfo.getLastName()) && !isBlank(ldapDetails.getLastName())) {
      userInfo.setLastName(ldapDetails.getLastName());
    }
    if (isBlank(userInfo.getEmail()) && !isBlank(ldapDetails.getEmail())) {
      userInfo.setEmail(ldapDetails.getEmail());
    }
  }

  @PostMapping(
      value = "/batchCreate",
      consumes = {MediaType.APPLICATION_JSON_VALUE})
  @ResponseBody
  public AjaxReturnObject<List<String>> batchCreateUsersAndGroups(
      @RequestBody UserImportResult usersAndGroups,
      HttpServletRequest request,
      Principal principal,
      HttpSession session) {

    User subject = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(subject);

    List<String> messages = new ArrayList<>();
    ErrorList errors = new ErrorList();

    createBatchOfUsersGroupsAndCommunities(
        request, principal, usersAndGroups, messages, errors, session);
    return new AjaxReturnObject<List<String>>(messages, errors);
  }

  private void createBatchOfUsersGroupsAndCommunities(
      HttpServletRequest request,
      Principal principal,
      UserImportResult userImportResult,
      List<String> messages,
      ErrorList errors,
      HttpSession session) {

    User subject = userManager.getUserByUsername(principal.getName());

    List<User> usersToSave = getUsersFromBatchResults(userImportResult.getParsedUsers());
    Map<String, User> usersMap = getUsersMapFromList(usersToSave);

    List<Group> groupsToSave = getGroupsFromBatchResults(userImportResult.getParsedGroups());
    List<CommunityPublicInfo> communitiesInfo = userImportResult.getParsedCommunities();

    if (properties.isSSO()) {
      for (User user : usersToSave) {
        user.setPassword(remoteUserPolicy.getPassword());
        user.setConfirmPassword(remoteUserPolicy.getPassword());
      }
    }
    if (properties.isLdapAuthenticationEnabled()) {
      for (User user : usersToSave) {
        user.setPassword(RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD);
        user.setConfirmPassword(RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD);
        user.setSignupSource(SignupSource.LDAP);
      }
    }

    validateUsers(usersToSave, errors, subject);
    validateGroups(usersMap, groupsToSave, errors);
    validateCommunities(usersMap, groupsToSave, communitiesInfo, errors);

    if (errors.hasErrorMessages()) {
      return; // don't start until validation errors are fixed
    }

    log.info("Saving {} users.", usersToSave.size());
    userEnablementUtils.checkLicenseForUserInRole(usersToSave.size(), Role.USER_ROLE);
    ProgressMonitor monitor = createProgressMonitor(usersToSave, groupsToSave);
    setBatchRegistrationProgress(monitor, session);
    monitor.setDescription("Creating users");
    for (User newUser : usersToSave) {
      try {
        userManager.saveNewUser(newUser);
        if (!newUser.isContentInitialized()) {
          initializer.init(newUser.getId());
        }
        auditService.notify(new GenericEvent(subject, newUser, AuditAction.CREATE));
        String createdMsg = getText("system.createAccount.batchUpload.userCreated");
        addUserSuccessMsg(messages, newUser.getUsername(), createdMsg);
        log.info(createdMsg);

      } catch (UserExistsException e) {
        String msg =
            getText(
                "system.createAccount.batchUpload.error.userExists",
                new Object[] {newUser.getUsername(), newUser.getEmail()});
        errors.addErrorMsg(msg);
        log.warn(msg, e);

      } catch (IllegalAddChildOperation e) {
        String msg =
            getText(
                "system.createAccount.batchUpload.error.userInit",
                new Object[] {newUser.getUsername()});
        errors.addErrorMsg(msg);
        log.warn(msg, e);
      } finally {
        monitor.worked(10);
        monitor.setDescription(
            format(
                "Created user: %s. Registration is  %d%% complete ",
                newUser.getUsername(), (int) monitor.getPercentComplete()));
      }
    }
    monitor.setDescription("Creating groups");
    for (Group group : groupsToSave) {
      try {
        User[] usersInGroup = userImportResult.getUsersFromMemberString(group.getMemberString());
        Group savedGroup =
            grpStrategy.createAndSaveGroup(
                group.getDisplayName(),
                userImportResult.getUserFromUsername(group.getPis()),
                subject,
                GroupType.LAB_GROUP,
                usersInGroup);

        group.setId(savedGroup.getId());

        String createdMsg = getText("system.createAccount.batchUpload.groupCreated");
        addGroupSuccessMsg(messages, group.getUniqueName(), createdMsg);
        log.info(createdMsg);
      } catch (RuntimeException e) {
        String msg =
            getText(
                "system.createAccount.batchUpload.error.group",
                new Object[] {group.getUniqueName(), e.getMessage()});
        errors.addErrorMsg(msg);
        log.warn(msg, e);
      } finally {
        monitor.worked(10);
        monitor.setDescription(
            format(
                "Created group: %s. Registration is  %d%% complete ",
                group.getDisplayName(), (int) monitor.getPercentComplete()));
      }
    }

    if (communitiesInfo != null) {
      monitor.setDescription("Creating communities");
      for (CommunityPublicInfo communityInfo : communitiesInfo) {

        Community community = communityInfo.toCommunity();

        // add admins
        for (String adminUsername : communityInfo.getAdmins()) {
          User admin = userManager.getUserByUsername(adminUsername);
          community.addAdmin(admin);
        }
        // add groups
        if (communityInfo.getLabGroups() != null) {
          for (String groupDisplayName : communityInfo.getLabGroups()) {
            for (Group group : groupsToSave) {
              if (groupDisplayName.equals(group.getDisplayName())) {
                List<Long> groupIds = community.getGroupIds();
                if (groupIds == null) {
                  groupIds = new ArrayList<>();
                }
                groupIds.add(group.getId());
                community.setGroupIds(groupIds);
              }
            }
          }
        }

        try {
          Validate.notEmpty(community.getAdmins(), "Community's admin list can't be empty");
          try {
            communityService.saveNewCommunity(community, subject); // (community);
            for (User admin : community.getAdmins()) {
              permFac.createCommunityPermissionsForAdmin(admin, community);
              userManager.save(admin);
            }
            auditService.notify(new GenericEvent(subject, community, AuditAction.CREATE));
            String createdMsg = getText("system.createAccount.batchUpload.communityCreated");
            addCommunitySuccessMsg(messages, communityInfo.getUniqueName(), createdMsg);
            log.info(createdMsg);

          } catch (DataAccessException dae) {
            // probably because not unique?
            errors.addErrorMsg(getText("errors.notUnique", new Object[] {"Unique name"}));
          }

        } catch (RuntimeException e) {
          String msg =
              getText(
                  "system.createAccount.batchUpload.error.community",
                  new Object[] {community.getUniqueName(), e.getMessage()});
          errors.addErrorMsg(msg);
          log.warn(msg, e);
        }
      }
    }

    monitor.setDescription("Notifying new users");
    for (User newUser : usersToSave) {
      try {
        // newUser is transient, so we need to get the persisted one,
        // which will load up groups if need be
        User persisted = userManager.getUserByUsername(newUser.getUsername());

        /*
         * if persisted user has a different email address then they
         * existed before, so there was UserExistsException on user
         * creation, and that's probably a completely different user.
         * But if email is the same it might be re-run of the batch
         * registration, with new groups, and we should send an email.
         */
        if (newUser.getEmail().equals(persisted.getEmail())) {
          postUserCreationSetup.postUserCreate(persisted, request, newUser.getPassword());

          String notifiedMsg = getText("system.createAccount.batchUpload.userNotified");
          addUserSuccessMsg(messages, newUser.getUsername(), notifiedMsg);
          log.info(notifiedMsg);
        }
      } catch (RuntimeException e) {
        String msg =
            getText(
                "system.createAccount.batchUpload.error.notifyUser",
                new Object[] {newUser.getUsername(), newUser.getEmail()});
        errors.addErrorMsg(msg);
        log.warn(msg, e);
      }
    }

    String importCompleteMsg = getText("system.createAccount.batchUpload.complete");
    monitor.setDescription(importCompleteMsg);
    monitor.done();
    messages.add(importCompleteMsg);
  }

  private ProgressMonitor createProgressMonitor(List<User> usersToSave, List<Group> groupsToSave) {
    if (usersToSave.size() + groupsToSave.size() == 0) {
      return ProgressMonitor.NULL_MONITOR;
    } else {
      return new ProgressMonitorImpl(
          (usersToSave.size() + groupsToSave.size()) * 10, "Uploading users");
    }
  }

  private void setBatchRegistrationProgress(ProgressMonitor monitor, HttpSession session) {
    session.setAttribute(BATCH_REGISTRATION_PROGRESS, monitor);
  }

  private List<User> getUsersFromBatchResults(List<UserRegistrationInfo> usersToSave) {
    List<User> users = new ArrayList<>();
    if (usersToSave != null) {
      for (UserRegistrationInfo userInfo : usersToSave) {
        users.add(userInfo.toUser());
      }
    }
    return users;
  }

  private Map<String, User> getUsersMapFromList(List<User> usersToSave) {
    Map<String, User> usersMap = new HashMap<>();
    if (usersToSave != null) {
      for (User user : usersToSave) {
        usersMap.put(user.getUsername(), user);
      }
    }
    return usersMap;
  }

  private List<Group> getGroupsFromBatchResults(List<GroupPublicInfo> groupsToSave) {
    List<Group> groups = new ArrayList<>();
    if (groupsToSave != null) {
      for (GroupPublicInfo groupInfo : groupsToSave) {
        groups.add(groupInfo.toGroup());
      }
    }
    return groups;
  }

  private ErrorList validateUsers(Collection<User> usersToSave, ErrorList errors, User subject) {
    List<User> validUsers = new ArrayList<>();
    for (User user : usersToSave) {

      Map<String, String> basicValidationErrors =
          inputValidator.validateAndGetErrorMessages(user, userValidator);
      if (basicValidationErrors.isEmpty()) {
        validUsers.add(user);
      } else {
        for (Entry<String, String> fieldErrorMsg : basicValidationErrors.entrySet()) {
          addUserFieldErrorMsg(
              errors, user.getUsername(), fieldErrorMsg.getKey(), fieldErrorMsg.getValue());
        }
      }
    }

    List<User> validUnrepeatedUsers = addErrorsForRepeatedUsers(validUsers, errors);

    for (User user : validUnrepeatedUsers) {
      boolean userExists = userManager.userExists(user.getUsername());
      if (userExists) {
        addUserFieldErrorMsg(
            errors,
            user.getUsername(),
            "username",
            getText("system.createAccount.batchUpload.error.existingUsername"));
      }

      List<User> userByEmail = userManager.getUserByEmail(user.getEmail());
      if (!userByEmail.isEmpty()) {
        addUserFieldErrorMsg(
            errors,
            user.getUsername(),
            "email",
            getText("system.createAccount.batchUpload.error.existingEmail"));
      }

      if (Constants.SYSADMIN_ROLE.equals(user.getRole())) {
        if (!subject.hasRole(Role.SYSTEM_ROLE)) {
          addUserFieldErrorMsg(
              errors,
              user.getUsername(),
              "",
              getText("system.createAccount.batchUpload.error.creatingSysadminWithoutSystemRole"));
        }
      }
    }

    return errors;
  }

  /* returns list without users with repeated username or email */
  private List<User> addErrorsForRepeatedUsers(List<User> validUsers, ErrorList errors) {

    Set<String> seenUsernames = new HashSet<>();
    Set<String> repeatedUsernames = new LinkedHashSet<>();

    Set<String> seenEmails = new HashSet<>();
    Set<String> repeatedEmails = new HashSet<>();

    for (User user : validUsers) {
      if (!seenUsernames.add(user.getUsername())) {
        repeatedUsernames.add(user.getUsername());
      }
      if (!seenEmails.add(user.getEmail())) {
        repeatedEmails.add(user.getEmail());
      }
    }

    for (String username : repeatedUsernames) {
      addUserFieldErrorMsg(
          errors,
          username,
          "username",
          getText("system.createAccount.batchUpload.error.repeatedUsername"));
    }

    for (User user : validUsers) {
      if (repeatedEmails.contains(user.getEmail())) {
        addUserFieldErrorMsg(
            errors,
            user.getUsername(),
            "email",
            getText("system.createAccount.batchUpload.error.repeatedEmail"));
      }
    }

    List<User> result = new ArrayList<>();
    for (User user : validUsers) {
      if (!repeatedUsernames.contains(user.getUsername())
          && !repeatedEmails.contains(user.getEmail())) {
        result.add(user);
      }
    }
    return result;
  }

  private void validateGroups(
      Map<String, User> usersMap, List<Group> groupsToSave, ErrorList errors) {

    for (Group group : groupsToSave) {
      Map<String, String> basicValidationErrors =
          inputValidator.validateAndGetErrorMessages(group, groupValidator);
      if (basicValidationErrors.isEmpty()) {
        if (group.getDisplayName().contains(",")) {
          addGroupErrorMsg(
              errors,
              group.getUniqueName(),
              "displayName",
              getText("system.createAccount.batchUpload.error.groupDisplayNameWithComma"));
        }
      } else {
        for (Entry<String, String> fieldErrorMsg : basicValidationErrors.entrySet()) {
          String field = getGroupInfoFieldNameForGroupValidatorFieldName(fieldErrorMsg.getKey());
          addGroupErrorMsg(errors, group.getUniqueName(), field, fieldErrorMsg.getValue());
        }
      }

      // ensure PI is know user with pi role
      String pi = group.getPis();
      if (StringUtils.isNotBlank(pi)) {
        User piUser = usersMap.get(pi);
        if (piUser == null) {
          addGroupErrorMsg(
              errors,
              group.getUniqueName(),
              "pi",
              getText("system.createAccount.batchUpload.error.unknownUser", new Object[] {pi}));
        } else if (!Constants.PI_ROLE.equals(piUser.getRole())) {
          addGroupErrorMsg(
              errors,
              group.getUniqueName(),
              "pi",
              getText("system.createAccount.batchUpload.error.piWithoutPiRole"));
        }
      }

      // ensure known users in member string
      for (String member : group.getMemberString()) {
        if (member.equals(pi)) {
          continue; // pi is in memberString, but has been already
          // checked
        }
        if (usersMap.get(member) == null) {
          addGroupErrorMsg(
              errors,
              group.getUniqueName(),
              "otherMembers",
              getText("system.createAccount.batchUpload.error.unknownUser", new Object[] {member}));
        }
      }
    }
  }

  private void validateCommunities(
      Map<String, User> usersMap,
      List<Group> groupsToSave,
      List<CommunityPublicInfo> communitiesInfo,
      ErrorList errors) {

    if (communitiesInfo == null) {
      return;
    }
    for (CommunityPublicInfo communityInfo : communitiesInfo) {
      Community community = communityInfo.toCommunity();
      Map<String, String> validationErrors =
          inputValidator.validateAndGetErrorMessages(community, communityValidator);

      if (!validationErrors.isEmpty()) {
        for (Entry<String, String> fieldErrorMsg : validationErrors.entrySet()) {
          String field = fieldErrorMsg.getKey();
          if ("adminIds".equals(field)) {
            // ignoring admin ids empty error, it's because admin users are not yet created
          } else {
            addCommunityErrorMsg(
                errors, community.getUniqueName(), field, fieldErrorMsg.getValue());
          }
        }
      }

      // check that admins are in usersMap and have valid role
      List<String> communityAdmins = communityInfo.getAdmins();
      if (CollectionUtils.isEmpty(communityAdmins)) {
        addCommunityErrorMsg(
            errors,
            community.getUniqueName(),
            "admins",
            getText("errors.required", new Object[] {"Choosing an administrator"}));
      } else {
        for (String admin : communityAdmins) {
          User adminUser = usersMap.get(admin);
          if (adminUser == null) {
            addCommunityErrorMsg(
                errors,
                community.getUniqueName(),
                "admins",
                getText(
                    "system.createAccount.batchUpload.error.unknownUser", new Object[] {admin}));
          } else if (!Constants.ADMIN_ROLE.equals(adminUser.getRole())
              && !Constants.SYSADMIN_ROLE.equals(adminUser.getRole())) {
            addCommunityErrorMsg(
                errors,
                community.getUniqueName(),
                "admins",
                getText(
                    "system.createAccount.batchUpload.error.communityAdminWithoutAdminRole",
                    new Object[] {admin}));
          }
        }
      }

      // check that groups on groupsToSave list
      List<String> communityLabGroups = communityInfo.getLabGroups();
      if (communityLabGroups != null) {
        for (String groupName : communityLabGroups) {
          if (!isBlank(groupName)) {
            boolean groupFound = false;
            for (Group groupToCreate : groupsToSave) {
              if (groupName.equals(groupToCreate.getDisplayName())) {
                groupFound = true;
                break;
              }
            }
            if (!groupFound) {
              addCommunityErrorMsg(
                  errors,
                  community.getUniqueName(),
                  "labGroups",
                  getText(
                      "system.createAccount.batchUpload.error.unknownGroup",
                      new Object[] {groupName}));
            }
          }
        }
      }
    }
  }

  // GroupPublicInfo has slightly different field names that Group
  private String getGroupInfoFieldNameForGroupValidatorFieldName(String groupFieldName) {
    if ("pis".equals(groupFieldName)) {
      return "pi";
    } else if ("memberString".equals(groupFieldName)) {
      return "otherMembers";
    }
    return groupFieldName;
  }

  private void addUserFieldErrorMsg(
      ErrorList errors, String username, String field, String errorMsg) {
    errors.addErrorMsg("U." + username + "." + field + "." + errorMsg);
  }

  private void addUserSuccessMsg(List<String> messages, String username, String msg) {
    messages.add("U." + username + "." + msg);
  }

  private void addGroupErrorMsg(
      ErrorList errors, String groupUniqueName, String field, String errorMsg) {
    errors.addErrorMsg("G." + groupUniqueName + "." + field + "." + errorMsg);
  }

  private void addGroupSuccessMsg(List<String> messages, String groupUniqueName, String msg) {
    messages.add("G." + groupUniqueName + "." + msg);
  }

  private void addCommunityErrorMsg(
      ErrorList errors, String uniqueName, String field, String errorMsg) {
    errors.addErrorMsg("C." + uniqueName + "." + field + "." + errorMsg);
  }

  private void addCommunitySuccessMsg(List<String> messages, String uniqueName, String msg) {
    messages.add("C." + uniqueName + "." + msg);
  }

  /*
   * =============== for tests ===============
   */
  protected void setUserLdapRepo(UserLdapRepo userLdapRepo) {
    this.userLdapRepo = userLdapRepo;
  }
}
