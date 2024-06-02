package com.researchspace.api.v1.controller;

import static com.researchspace.service.UserDeletionPolicy.UserTypeRestriction.TEMP_USER;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang.StringUtils.abbreviate;
import static org.apache.commons.lang.StringUtils.join;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.researchspace.api.v1.SysadminApi;
import com.researchspace.api.v1.model.ApiGroupInfo;
import com.researchspace.api.v1.model.ApiSysadminUserSearchResult;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.auth.WhiteListIPChecker;
import com.researchspace.core.util.DateUtil;
import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.ExportSelection.ExportType;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.SysadminUserCreationHandler;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.service.UserDeletionManager;
import com.researchspace.service.UserDeletionPolicy;
import com.researchspace.service.UserDeletionPolicy.UserTypeRestriction;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.DeploymentProperty;
import com.researchspace.webapp.controller.SysAdminCreateUser;
import com.researchspace.webapp.controller.UserExportHandler;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.servlet.ServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * This is 'hidden' api usable just for us to manage extinct users on community.r.c
 * programmatically. It is not to be documented for public use
 *
 * <ul>
 *   <li>All methods must assert that sysadmin user is making the call
 * </ul>
 */
@ApiController
public class SysadminApiController extends BaseApiController implements SysadminApi {

  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  @Value("${sysadmin.nodeletenewerthan.days:366}")
  private Integer ageLimit = 366;

  private @Autowired WhiteListIPChecker ipWhiteListChecker;
  private @Autowired UserManager userMgr;
  private @Autowired UserDeletionManager userDelMgr;
  private @Autowired UserExportHandler userExportHandler;
  private @Autowired SysadminUserCreationHandler sysadminUserCreationHandler;
  private @Autowired UserApiKeyManager apiKeyMgr;
  private @Autowired IGroupCreationStrategy grpStrategy;
  private @Autowired AuditTrailService auditService;
  private @Autowired IContentInitializer initialiser;
  private @Autowired UserEnablementUtils userEnablementUtils;

  void assertIsSysadmin(User subject, ServletRequest request) {
    if (!subject.hasRole(Role.SYSTEM_ROLE)
        || !ipWhiteListChecker.isRequestWhitelisted(request, subject, SECURITY_LOG)) {
      throw new AuthorizationException("Sysadmin role with valid IP required for admin operations");
    }
  }

  @Override
  public void deleteTempUserOlderThan1Year(
      ServletRequest req,
      @PathVariable("id") Long toDeleteId,
      @RequestAttribute(name = "user") User sysadmin) {
    assertIsSysadmin(sysadmin, req);
    User toDelete = userMgr.get(toDeleteId);
    validateTempUser(toDelete);

    ServiceOperationResult<User> result =
        removeUser(toDeleteId, sysadmin, new UserDeletionPolicy(TEMP_USER));
    processResult(result);
  }

  private void validateTempUser(User toDelete) {
    if (!toDelete.isTempAccount() || userIsTooNew(toDelete)) {
      throw new IllegalArgumentException("Can only delete temp users created more than a year ago");
    }
  }

  private void validateNonTempUser(User toDelete) {
    if (userIsTooNew(toDelete)) {
      throw new IllegalArgumentException("Can only delete  users created more than a year ago");
    }
  }

  private void processResult(ServiceOperationResult<User> result) {
    if (!result.isSucceeded()) {
      // this will lead to unprocessable entity 422 return code; deletion may fail
      // e.g. if deleting an admin as well as internal errors
      throw new IllegalArgumentException(result.getMessage());
    }
  }

  @Override
  public void deleteAnyUserOlderThan1Year(
      ServletRequest req,
      @RequestParam(name = "maxLastLogin", required = false, defaultValue = "2100-01-01")
          @DateTimeFormat(pattern = "yyyy-MM-dd")
          Date maxLastLogin,
      @PathVariable("id") Long toDeleteId,
      @RequestAttribute(name = "user") User sysadmin) {
    assertIsSysadmin(sysadmin, req);
    User toDelete = userMgr.get(toDeleteId);
    validateNonTempUser(toDelete);
    UserDeletionPolicy deletionPolicy = new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION);
    deletionPolicy.setStrictPreserveDataForGroup(true);
    if (maxLastLogin.after(oneYearAgo())) {
      maxLastLogin = oneYearAgo();
    }
    deletionPolicy.setLastLoginCutOffForGroup(maxLastLogin);

    ServiceOperationResult<User> result =
        userDelMgr.isUserRemovable(toDeleteId, deletionPolicy, sysadmin);
    if (result.isSucceeded()) {
      log.info("user {} is deletable, making export", toDelete.getUsername());
      try {
        doUserExport(sysadmin, toDeleteId);
      } catch (Exception e) {
        throw new IllegalStateException(
            "Could not make export of user's work before deleting account; user deletion aborted");
      }
      ServiceOperationResult<User> result2 = removeUser(toDeleteId, sysadmin, deletionPolicy);
      processResult(result2);
      log.info(
          "user {} ({}) files and DB completely deleted", toDelete.getUsername(), toDelete.getId());
    } else {
      processResult(result);
    }
  }

  private ArchiveResult doUserExport(User sysadmin, Long userId) throws Exception {
    User toexport = userMgr.get(userId);
    if (!toexport.isContentInitialized()) {
      log.warn(
          "User [{}] has never logged in, there is nothing to archive!", toexport.getUsername());
      return null;
    }
    ExportSelection selection = new ExportSelection();
    selection.setType(ExportType.USER);
    selection.setUsername(toexport.getUsername());
    ArchiveExportConfig config = new ArchiveExportConfig();
    config.setArchiveType(ArchiveExportConfig.XML);

    Future<ArchiveResult> archive =
        userExportHandler.doUserArchive(
            selection, config, sysadmin, new URI(properties.getServerUrl()));
    ArchiveResult result = archive.get(); // wait for completion.
    return result;
  }

  Date oneYearAgo() {
    return java.sql.Date.valueOf(LocalDate.now().minusYears(1));
  }

  @Override
  public ApiSysadminUserSearchResult getUsers(
      ServletRequest req,
      @Valid SysadminUserPaginationCriteria pgCriteria,
      @Valid ApiSystemUserSearchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User sysadmin)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    assertIsSysadmin(sysadmin, req);

    var internalPgCrit = convertApiParamsToInternalParams(pgCriteria, srchConfig);

    var internalsearchResults = userMgr.getViewableUsers(sysadmin, internalPgCrit);
    List<ApiUser> users = new ArrayList<>();
    ApiSysadminUserSearchResult searchResults = new ApiSysadminUserSearchResult();
    convertISearchResults(
        pgCriteria,
        srchConfig,
        sysadmin,
        internalsearchResults,
        searchResults,
        users,
        searchResult -> new ApiUser(searchResult),
        apiUser -> {});
    return searchResults;
  }

  private ServiceOperationResult<User> removeUser(
      Long toDeleteId, User sysadmin, UserDeletionPolicy policy) {
    var result = userDelMgr.removeUser(toDeleteId, policy, sysadmin);
    if (result.isSucceeded()) {
      auditService.notify(new GenericEvent(sysadmin, result.getEntity(), AuditAction.DELETE));
    }
    if (result.isSucceeded() && properties.getDeleteUserResourcesImmediately()) {
      userDelMgr.deleteRemovedUserFilestoreResources(toDeleteId, true, sysadmin);
    }
    return result;
  }

  private boolean userIsTooNew(User toDelete) {
    if (ageLimit <= 0) {
      return false;
    }
    return toDelete
        .getCreationDate()
        .after(DateUtil.localDateToDateUTC(LocalDate.now().minusDays(ageLimit)));
  }

  private PaginationCriteria<User> convertApiParamsToInternalParams(
      SysadminUserPaginationCriteria pgCriteria, ApiSystemUserSearchConfig srchConfig) {

    var internalPgCrit = getPaginationCriteriaForApiSearch(pgCriteria, User.class);
    UserSearchCriteria sc = new UserSearchCriteria();
    if (srchConfig != null) {
      if (srchConfig.getCreatedBefore() != null) {
        sc.setCreationDateEarlierThan(srchConfig.getCreatedBefore());
      }
      if (srchConfig.getLastLoginBefore() != null) {
        sc.setLastLoginEarlierThan(srchConfig.getLastLoginBefore());
      }
      sc.setTempAccountsOnly(srchConfig.isTempAccountsOnly());
    }
    log.info("Searching for users with criteria {}", srchConfig);
    internalPgCrit.setSearchCriteria(sc);
    return internalPgCrit;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class UserApiPost {
    @NotBlank private String username;

    @Size(min = 8)
    private String password;

    @Email private String email;

    @Size(min = 1)
    private String firstName;

    @Size(min = 1)
    private String lastName;

    @Pattern(regexp = "(ROLE_USER)|(ROLE_PI)|(ROLE_ADMIN)|(ROLE_SYSADMIN)")
    private String role;

    @Size(min = 1)
    private String affiliation;

    @Size(min = 16, max = 32)
    @Pattern(regexp = UserApiKey.APIKEY_REGEX)
    private String apiKey;

    /** Whether or not to create a group for a PI user. */
    @Builder.Default private boolean createGroupForPi = false;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class GroupApiPost {
    @NotBlank(message = "Please provide a name for the group")
    private String displayName;

    @Size(min = 1, max = 20, message = "Group must contain between {min} and {max} members")
    @Valid
    @JsonAlias({"members", "users"})
    private List<UserGroupPost> users = new ArrayList<>();

    private GroupType type = GroupType.LAB_GROUP;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class UserGroupPost {
    @NotBlank(message = "Username must be provided")
    private String username;

    @Pattern(regexp = "(DEFAULT)|(RS_LAB_ADMIN)|(PI)|(GROUP_OWNER)")
    private String roleInGroup;
  }

  @Override
  @DeploymentProperty(DeploymentPropertyType.API_BETA_ENABLED)
  public ApiUser createUser(
      ServletRequest req,
      @Valid @RequestBody UserApiPost userApi,
      BindingResult errors,
      @RequestAttribute(name = "user") User sysadmin)
      throws BindException {

    assertIsSysadmin(sysadmin, req);
    if (errors.hasErrors()) {
      log.warn("binding error on createUser api request: {}", errors);
      throw new BindException(errors);
    }
    SysAdminCreateUser userForm = createSysadminUserObjectFromApi(userApi);

    var aroAjaxReturnObject = sysadminUserCreationHandler.createUser(userForm, sysadmin);
    if (aroAjaxReturnObject.getData() != null) {
      if (!StringUtils.isBlank(userApi.getApiKey())) {
        UserApiKey key = new UserApiKey(aroAjaxReturnObject.getData(), userApi.getApiKey());
        apiKeyMgr.save(key);
        log.info("Creating API key, set as {}", abbreviate(userApi.getApiKey(), 6));
      }
      return new ApiUser(aroAjaxReturnObject.getData());
    } else {
      throw new IllegalArgumentException(
          aroAjaxReturnObject.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy(","));
    }
  }

  @Override
  @DeploymentProperty(DeploymentPropertyType.API_BETA_ENABLED)
  public void enableUser(
      ServletRequest req,
      @RequestAttribute(name = "user") User sysadmin,
      @PathVariable("userId") Long userId)
      throws BindException {
    performEnableUser(sysadmin, req, userId, true);
  }

  @Override
  @DeploymentProperty(DeploymentPropertyType.API_BETA_ENABLED)
  public void disableUser(
      ServletRequest req,
      @RequestAttribute(name = "user") User sysadmin,
      @PathVariable("userId") Long userId)
      throws BindException {
    performEnableUser(sysadmin, req, userId, false);
  }

  private void performEnableUser(User sysadmin, ServletRequest req, Long userId, boolean enable) {
    assertIsSysadmin(sysadmin, req);
    User userToAmend = userMgr.get(userId);
    if (enable != userToAmend.isEnabled()) {
      if (enable) {
        userEnablementUtils.checkLicenseForUserInRole(1, userToAmend.getRoles().iterator().next());
      }
      userToAmend.setEnabled(enable);
      userMgr.save(userToAmend);

      userEnablementUtils.auditUserEnablementChangeEvent(enable, userToAmend);
      userEnablementUtils.notifyByEmailUserEnablementChange(userToAmend, sysadmin, enable);
    }
  }

  @Override
  @DeploymentProperty(DeploymentPropertyType.API_BETA_ENABLED)
  public ApiGroupInfo createGroup(
      ServletRequest req,
      @Valid @RequestBody GroupApiPost groupApiPost,
      BindingResult errors,
      @RequestAttribute(name = "user") User sysadmin)
      throws BindException {

    assertIsSysadmin(sysadmin, req);
    // quick validation
    if (errors.hasErrors()) {
      log.warn("binding error on createGroup api request: {}", errors);
      throw new BindException(errors);
    }
    // create any users no existing
    var existingUserMap =
        groupApiPost.getUsers().stream()
            .map(UserGroupPost::getUsername)
            .collect(partitioningBy(username -> userMgr.userExists(username)));

    if (!existingUserMap.get(Boolean.FALSE).isEmpty()) {
      String missingUsers = join(existingUserMap.get(Boolean.FALSE), ",");
      throw new IllegalArgumentException(
          "Please create these users before creating a group: " + missingUsers);
    }

    Map<User, RoleInGroup> users =
        groupApiPost.getUsers().stream()
            .collect(
                toMap(
                    ugp -> userMgr.getUserByUsername(ugp.getUsername()),
                    ugp -> RoleInGroup.valueOf(ugp.getRoleInGroup())));

    Group group = apiGroupToGroup(groupApiPost);

    if (!group.isProjectGroup()) {
      List<User> piUsers =
          users.entrySet().stream()
              .filter(user -> user.getValue().equals(RoleInGroup.PI))
              .map(Map.Entry::getKey)
              .collect(Collectors.toList());
      if (piUsers.size() != 1) {
        throw new IllegalArgumentException("Exactly one user must be the group's PI.");
      }
      User piUser = piUsers.get(0);
      if (!piUser.hasRole(Role.PI_ROLE)) {
        throw new IllegalArgumentException("User selected as group's PI must have PI role.");
      }
      group.setOwner(piUser);
    } else {
      if (group.getGroupOwners() == null || group.getGroupOwners().isEmpty()) {
        throw new IllegalArgumentException("Project group should have at least 1 GROUP_OWNER.");
      }
      group.setOwner(sysadmin);
    }

    List<User> usersToAdd = new ArrayList<>();
    // init users if need be
    for (User u : users.keySet()) {
      usersToAdd.add(u);
      if (!u.isContentInitialized()) {
        initialiser.init(u.getId());
      }
    }

    group = grpStrategy.createAndSaveGroup(group, group.getOwner(), usersToAdd);
    return new ApiGroupInfo(group);
  }

  private Group apiGroupToGroup(GroupApiPost groupApiPost) {
    Group group = new Group();
    group.setDisplayName(groupApiPost.displayName);
    group.setGroupType(groupApiPost.getType());
    group.setGroupOwners(combineUsersByRole(groupApiPost.getUsers(), RoleInGroup.GROUP_OWNER));
    group.setPis(combineUsersByRole(groupApiPost.getUsers(), RoleInGroup.PI));
    group.setAdmins(combineUsersByRole(groupApiPost.getUsers(), RoleInGroup.RS_LAB_ADMIN));
    return group;
  }

  private String combineUsersByRole(List<UserGroupPost> users, RoleInGroup role) {
    return users.stream()
        .filter(user -> user.roleInGroup.equals(role.name()))
        .map(UserGroupPost::getUsername)
        .collect(Collectors.joining(","));
  }

  private SysAdminCreateUser createSysadminUserObjectFromApi(UserApiPost userApi) {
    SysAdminCreateUser userForm = new SysAdminCreateUser();
    userForm.setUsername(userApi.getUsername());
    userForm.setPassword(userApi.getPassword());
    userForm.setPasswordConfirmation(userApi.getPassword());
    userForm.setEmail(userApi.getEmail());
    userForm.setAffiliation(userApi.getAffiliation());
    userForm.setFirstName(userApi.getFirstName());
    userForm.setLastName(userApi.getLastName());
    userForm.setRole(userApi.getRole());
    userForm.setCreateGroupForPiUser(userApi.isCreateGroupForPi());
    return userForm;
  }
}
