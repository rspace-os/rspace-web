package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.model.views.ServiceOperationResult.convertToStringEntity;
import static java.lang.Boolean.TRUE;

import com.researchspace.admin.service.SysAdminManager;
import com.researchspace.admin.service.UsageListingDTO;
import com.researchspace.admin.service.UsageListingDTO.PaginationCriteriaDTO;
import com.researchspace.admin.service.UsageListingDTO.UserInfoListDTO;
import com.researchspace.admin.service.UsageListingDTO.UserStatisticsDTO;
import com.researchspace.admin.service.UserUsageInfo;
import com.researchspace.archive.ArchiveResult;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.util.DefaultURLPaginator;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.SortOrder;
import com.researchspace.licenseserver.model.License;
import com.researchspace.licensews.LicenseServerUnavailableException;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dtos.CommunityValidator;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.dtos.ExportSelection.ExportType;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.model.dtos.PiToUserCommandValidator;
import com.researchspace.model.dtos.RunAsUserCommand;
import com.researchspace.model.dtos.UserRoleChangeCmnd;
import com.researchspace.model.dtos.UserSearchCriteria;
import com.researchspace.model.dtos.UserTagData;
import com.researchspace.model.dtos.UserToPiCommandValidator;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.IUserPermissionUtils;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.views.CommunityListResult;
import com.researchspace.model.views.GroupListResult;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.UserStatistics;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.RoleManager;
import com.researchspace.service.SysadminUserCreationHandler;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserDeletionManager;
import com.researchspace.service.UserDeletionPolicy;
import com.researchspace.service.UserDeletionPolicy.UserTypeRestriction;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserRoleHandler;
import com.researchspace.service.UserStatisticsManager;
import com.researchspace.service.UserTagManager;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import com.researchspace.service.impl.StrictEmailContentGenerator;
import com.researchspace.webapp.filter.IUserAccountLockoutPolicy;
import java.net.URI;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;

@Controller("sysAdminController")
@RequestMapping("/system")
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
@SessionAttributes("communityGroups")
public class SysAdminController extends BaseController {

  static final String UNKNOWN = "Unknown";
  protected static final String REDIRECT_SYSTEM_COMMUNITY_LIST = "redirect:/community/admin/list";
  protected static final String SYSTEM_CREATE_COMMUNITY_VIEW = "system/createCommunity";
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;
  private @Autowired PermissionFactory permFac;
  private @Autowired UserDeletionManager userDeletionMgr;
  private @Autowired UserStatisticsManager userStatisticsManager;
  private @Autowired IReauthenticator reauthenticator;
  private @Autowired CommunityServiceManager communityService;
  private @Autowired RoleManager roleMger;

  private @Autowired CommunityValidator communityValidator;

  private @Autowired UserRoleHandler userRoleHandler;
  private @Autowired UserExportHandler userExportHandler;

  private @Autowired SysAdminManager sysMgr;
  private @Autowired IUserPermissionUtils userPermissionUtils;
  private @Autowired UserEnablementUtils userEnablementUtils;
  private @Autowired SysadminCreateUserFormConfigurer createUserFormConfigurer;
  private @Autowired SysadminUserCreationHandler sysadminUserCreationHandler;
  private @Autowired IUserAccountLockoutPolicy lockoutPolicy;
  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;

  @Autowired
  @Qualifier("emailBroadcast")
  private EmailBroadcast emailer;

  @Value("${archive.folder.storagetime:48}")
  private String storageTimeProperty = "48";

  /** Basic get request for page */
  @GetMapping
  public ModelAndView getSystemPage(Model model) {
    User userInSession = userManager.getAuthenticatedUserInSession();
    if (userInSession.hasRole(Role.ADMIN_ROLE)) {
      boolean hasCommunity = communityService.hasCommunity(userInSession);
      model.addAttribute("hasCommunity", hasCommunity);
    }
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(userInSession, "public_sharing"));
    return new ModelAndView("system/system");
  }

  @GetMapping("/createAccount")
  public ModelAndView getCreateAccountPage(Model model) {
    User userInSession = userManager.getAuthenticatedUserInSession();
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(userInSession, "public_sharing"));
    return new ModelAndView("system/createAccount");
  }

  /*  */
  /** Reloads table e.g., after pagination click */
  /*
  @GetMapping("/ajax/list")
  public ModelAndView ajaxSystemUserList(
      Principal principal,
      Model model,
      PaginationCriteria<User> pgCrit,
      UserSearchCriteria srcCrit) {

    pgCrit.setSearchCriteria(srcCrit);
    doUsageListing(principal, model, pgCrit);
    return new ModelAndView("system/system_list_ajax");
  }*/

  @GetMapping("/ajax/jsonList")
  @ResponseBody
  public UsageListingDTO getUsersAndUsageListing(
      Principal principal, PaginationCriteria<User> pgCrit, UserSearchCriteria srcCrit) {

    pgCrit.setSearchCriteria(srcCrit);
    return getUsageListingDTO(principal, pgCrit);
  }

  /**
   * Method to enable/disable user accounts.
   *
   * @param userId
   * @param enabled <code>true</code> to enable, <code>false</code> to disable account
   * @return an updated view of user list
   */
  @PostMapping("/ajax/setAccountEnablement")
  public ResponseEntity<Object> setUserAccountEnablement(
      @RequestParam("userId") Long userId, @RequestParam("enabled") boolean enabled) {

    User admin = userManager.getAuthenticatedUserInSession();
    User userToAmend = userManager.get(userId);
    userPermissionUtils.assertHasPermissionsOnTargetUser(
        admin, userToAmend, "Changing enablement state of user");
    if (enabled && !userToAmend.isEnabled()) {
      userEnablementUtils.checkLicenseForUserInRole(1, userToAmend.getRoles().iterator().next());
    }
    userToAmend.setEnabled(enabled);
    userManager.save(userToAmend);
    userEnablementUtils.auditUserEnablementChangeEvent(enabled, userToAmend);
    userEnablementUtils.notifyByEmailUserEnablementChange(userToAmend, admin, enabled);

    return ResponseEntity.status(HttpStatus.OK).build();
  }

  /**
   * Mechanism to unlock accounts when user has locked themselves out with too many password
   * attempts. RSPAC-1975
   *
   * @param userId
   * @return AjaxReturnObject<String>
   */
  @PostMapping("/ajax/unlockAccount")
  @ResponseBody
  public ResponseEntity<Object> unlockAccount(@RequestParam("userId") Long userId) {

    User admin = userManager.getAuthenticatedUserInSession();
    User userToUnlock = userManager.get(userId);
    // the account might no longer be locked by the time the sysadmin makes this call
    if (userToUnlock.isAccountLocked()) {
      userPermissionUtils.assertHasPermissionsOnTargetUser(
          admin, userToUnlock, "Unlocking user account");
      lockoutPolicy.forceUnlock(userToUnlock);
      userManager.save(userToUnlock);
      return ResponseEntity.status(HttpStatus.OK).build();
    }
    return getAjaxMessageResponseEntity(HttpStatus.BAD_REQUEST, "Account is already unlocked");
  }

  @PostMapping("/ajax/removeUserAccount")
  @ResponseBody
  public ResponseEntity<Object> removeUserAccount(@RequestParam("userId") Long userId) {

    if (!(TRUE.toString()).equalsIgnoreCase(properties.getDeleteUser())) {
      throw new IllegalStateException("Delete user is disabled!");
    }

    User sysadmin = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(sysadmin);

    try {
      // fail fast
      ServiceOperationResult<User> isDeletionPossible =
          userDeletionMgr.isUserRemovable(
              userId, new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION), sysadmin);
      if (!isDeletionPossible.isSucceeded()) {
        ServiceOperationResult<String> opResult =
            convertToStringEntity(isDeletionPossible, isDeletionPossible.getMessage());
        return getAjaxMessageResponseEntity(HttpStatus.BAD_REQUEST, opResult.getMessage());
      }
      User toDelete = isDeletionPossible.getEntity();

      log.info(
          "Making XML archive for user {} - ({}), before deleting", toDelete.getUsername(), userId);
      ArchiveResult exported = doUserExport(sysadmin, userId);
      ServiceOperationResult<User> internalResult =
          userDeletionMgr.removeUser(
              userId, new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION), sysadmin);
      ServiceOperationResult<String> returnResult =
          convertToStringEntity(internalResult, internalResult.getMessage());
      if (exported != null) {
        String messageAddendum =
            String.format(
                "An XML export was generated at %s and will remain for %s hours",
                exported.getExportFile().getAbsolutePath(), storageTimeProperty);
        log.info("user {} ({}) : {}", toDelete.getUsername(), userId, messageAddendum);
        returnResult =
            convertToStringEntity(
                internalResult, internalResult.getEntity() + " " + messageAddendum);
      }
      if (internalResult.isSucceeded()) {
        auditService.notify(
            new GenericEvent(sysadmin, internalResult.getEntity(), AuditAction.DELETE));
      }
      if (internalResult.isSucceeded() && properties.getDeleteUserResourcesImmediately()) {
        userDeletionMgr.deleteRemovedUserFilestoreResources(userId, true, sysadmin);
      }
      return getAjaxMessageResponseEntity(HttpStatus.OK, returnResult.getMessage());

    } catch (Exception dae) {
      log.error("Error deleting user:", dae);
      return getAjaxMessageResponseEntity(HttpStatus.BAD_REQUEST, dae.getMessage());
    }
  }

  private ArchiveResult doUserExport(User sysadmin, Long userId) throws Exception {
    User toexport = userManager.get(userId);
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

  /**
   * Gets dialog contents to initialise promotion to PI
   *
   * @param userId
   * @param model
   * @return
   */
  @GetMapping("/ajax/promoteToPI")
  public String getPromoteUserToPI(@RequestParam("userId") Long userId, Model model) {
    model.addAttribute(new UserRoleChangeCmnd());
    model.addAttribute("isVerificationPwdRequired", properties.isSSO());
    return "system/promoteToPI";
  }

  /*  @GetMapping("/ajax/grantPIRole")
  public String getGrantUserPiRole(@RequestParam("userId") Long userId, Model model) {
    model.addAttribute(new UserRoleChangeCmnd());
    model.addAttribute("isVerificationPwdRequired", properties.isSSO());
    return "system/grantPIRole";
  }

  @GetMapping("/ajax/revokePIRole")
  public String getRevokeUserPiRole(@RequestParam("userId") Long userId, Model model) {
    model.addAttribute(new UserRoleChangeCmnd());
    model.addAttribute("isVerificationPwdRequired", properties.isSSO());
    return "system/grantPIRole";
  }*/

  /**
   * Promotes user to PI form submission
   *
   * @param model
   * @param user2PiCommand
   * @param errors
   * @return
   */
  @PostMapping("/ajax/promoteToPI")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"sysadminPassword"})
  public String promoteUserToPIAndSetupGroupSubmit(
      Model model, @ModelAttribute UserRoleChangeCmnd user2PiCommand, BindingResult errors) {
    User admin = userManager.getAuthenticatedUserInSession();
    User newPI = userManager.get(user2PiCommand.getUserId());
    if (!validateInput(user2PiCommand, errors, admin, createUserPromotionVldator(newPI))) {
      return "system/promoteToPI";
    }
    userRoleHandler.promoteUserToPiWithGroup(admin, newPI, user2PiCommand);
    model.addAttribute("newPI", newPI);
    return "system/promoteToPI";
  }

  private Validator createUserPromotionVldator(User newPI) {
    return new UserToPiCommandValidator(getCurrentActiveUsers().getActiveUsers(), newPI);
  }

  private Validator createPiToUserVldator(User toDemote) {
    return new PiToUserCommandValidator(getCurrentActiveUsers().getActiveUsers(), toDemote);
  }

  @PostMapping("/ajax/grantPIRole")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"sysadminPassword"})
  public ResponseEntity<Object> grantPIRoleSubmit(
      @ModelAttribute UserRoleChangeCmnd user2PiCommand, BindingResult errors) {
    User admin = userManager.getAuthenticatedUserInSession();
    User newPI = userManager.get(user2PiCommand.getUserId());
    if (!validateInput(user2PiCommand, errors, admin, createUserPromotionVldator(newPI))) {
      return getAjaxMessageResponseEntity(HttpStatus.BAD_REQUEST, errors);
    }
    userRoleHandler.grantGlobalPiRoleToUser(admin, newPI);
    return ResponseEntity.status(HttpStatus.OK).build();
  }

  @PostMapping("/ajax/revokePIRole")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"sysadminPassword"})
  public ResponseEntity<Object> revokePIRoleSubmit(
      @ModelAttribute UserRoleChangeCmnd user2PiCommand, BindingResult errors) {
    User admin = userManager.getAuthenticatedUserInSession();
    User toDemote = userManager.get(user2PiCommand.getUserId());
    if (!validateInput(user2PiCommand, errors, admin, createPiToUserVldator(toDemote))) {
      return getAjaxMessageResponseEntity(HttpStatus.BAD_REQUEST, errors);
    }
    userRoleHandler.revokeGlobalPiRoleFromUser(admin, toDemote);
    return ResponseEntity.status(HttpStatus.OK).build();
  }

  private boolean validateInput(
      UserRoleChangeCmnd user2PiCommand, BindingResult errors, User admin, Validator validator) {
    rejectIfNotReauthenticated(user2PiCommand.getSysadminPassword(), errors, admin);
    inputValidator.validate(user2PiCommand, validator, errors);
    return !errors.hasErrors(); // return to form
  }

  protected @Autowired UserTagManager userTagManager;

  /**
   * Get tags for the list of users
   *
   * @return List<UserTagData> or a string with error message
   */
  @GetMapping("/users/getTagsForUsers")
  @ResponseBody
  public ResponseEntity<Object> getTagsForUsers(
      @RequestParam("userIds") List<Long> userIds, Principal principal) {

    User user = getUserByUsername(principal.getName());
    assertUserIsSysAdmin(user);

    List<UserTagData> userTags = userTagManager.getUserTags(userIds);
    return ResponseEntity.status(HttpStatus.OK).body(userTags);
  }

  /** Save tags for list of users */
  @PostMapping("/users/saveTagsForUsers")
  @ResponseBody
  public ResponseEntity<Object> saveTagsForUsers(
      @RequestBody List<UserTagData> userTags, Principal principal) {

    User currentUser = getUserByUsername(principal.getName());
    assertUserIsSysAdmin(currentUser);

    userTagManager.saveUserTags(userTags);
    return ResponseEntity.status(HttpStatus.OK).build();
  }

  /**
   * Starts returning list after 2+ characters
   *
   * @param tagFilter
   * @param principal
   * @return user tags in a list of strings, or an error message
   */
  @GetMapping("/users/allUserTags")
  @ResponseBody
  public ResponseEntity<Object> getAllUserTags(
      @RequestParam(value = "tagFilter", required = false, defaultValue = "") String tagFilter,
      Principal principal) {

    User currentUser = getUserByUsername(principal.getName());
    assertUserIsSysAdmin(currentUser);
    if (tagFilter == null || tagFilter.length() < 2) {
      return getAjaxMessageResponseEntity(
          HttpStatus.BAD_REQUEST, "'tagFilter' parameter required (min. 2 chars length)");
    }
    List<String> allTags = userTagManager.getAllUserTags(tagFilter);
    return ResponseEntity.status(HttpStatus.OK).body(allTags);
  }

  /**
   * Getter for role-specific creation
   *
   * @param role
   * @param principal
   * @param model
   * @return
   */
  @GetMapping("/ajax/createAccountForm")
  public ModelAndView createAccountForm(
      @RequestParam("role") String role, Principal principal, Model model) {
    User userInSession = userManager.getUserByUsername(principal.getName());
    model.addAttribute("user", userInSession);
    model.addAttribute("role", role);
    model.addAttribute("communities", getCommunitiesList(principal));
    model.addAttribute("usernamePattern", getUserNamePattern());
    model.addAttribute("usernamePatternTitle", getUserNameTitle());
    model.addAttribute("affiliationRequired", isAffiliationRequired());
    model.addAttribute("ldapLookupRequired", isLdapLookupRequired());
    model.addAttribute(
        "backdoorSysadminAccountCreationEnabled", isBackdoorSysadminCreationEnabled());
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(userInSession, "public_sharing"));
    return new ModelAndView("system/createUserAccountForm");
  }

  private Object isBackdoorSysadminCreationEnabled() {
    return createUserFormConfigurer.isBackdoorSysadminCreationEnabled();
  }

  private Boolean isLdapLookupRequired() {
    return createUserFormConfigurer.isDisplayLdapLookupRequired();
  }

  private Boolean isAffiliationRequired() {
    return createUserFormConfigurer.isAffiliationRequired();
  }

  private String getUserNameTitle() {
    return createUserFormConfigurer.getUsernamePatternTitle();
  }

  private String getUserNamePattern() {
    return createUserFormConfigurer.getUsernamePattern();
  }

  /**
   * Gets a list of communities and returns as ajax
   *
   * @param principal
   * @return An {@link AjaxReturnObject} wrapping a List of CommunityListResult, as json
   */
  @GetMapping("/ajax/getAllCommunities")
  @ResponseBody
  public AjaxReturnObject<List<CommunityListResult>> getAllCommunities(Principal principal) {
    List<CommunityListResult> communities = getCommunitiesList(principal);
    return new AjaxReturnObject<List<CommunityListResult>>(communities, null);
  }

  private List<CommunityListResult> getCommunitiesList(Principal principal) {
    User userInSession = userManager.getUserByUsername(principal.getName());
    List<Community> communityList = null;

    if (userInSession.hasRole(Role.ADMIN_ROLE)) {
      communityList = communityService.listCommunitiesForAdmin(userInSession.getId());
    } else if (userInSession.hasRole(Role.SYSTEM_ROLE)) {
      PaginationCriteria<Community> pg = new PaginationCriteria<Community>();
      pg.setResultsPerPage(Integer.MAX_VALUE); // setGetAllResults
      ISearchResults<Community> communities = communityService.listCommunities(userInSession, pg);
      communityList = communities.getResults();
    }

    List<CommunityListResult> communityDisplay = new ArrayList<>();
    if (communityList != null) {
      for (Community comm : communityList) {
        CommunityListResult r = new CommunityListResult(comm.getId(), comm.getDisplayName());
        communityDisplay.add(r);
      }
    }
    return communityDisplay;
  }

  @GetMapping("/ajax/generateRandomPassword")
  @ResponseBody
  public AjaxReturnObject<String> generateRandomPassword() {
    final int passwordLength = 8;
    return new AjaxReturnObject<>(
        RandomStringUtils.random(passwordLength, 0, 0, true, true, null, new SecureRandom()), null);
  }

  /**
   * Controller method to create a new user account.
   *
   * @param userForm SysAdminCreateUser
   * @param principal
   * @return
   */
  @PostMapping("/ajax/createUserAccount")
  @ResponseBody
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"password", "passwordConfirmation"})
  public AjaxReturnObject<String> createUserAccount(
      @Valid SysAdminCreateUser userForm, BindingResult errors, Principal principal) {
    // initial quick validation of required / minimal fields
    ErrorList errorList = new ErrorList();
    if (errors.hasErrors()) {
      inputValidator.populateErrorList(errors, errorList);
      return new AjaxReturnObject<String>(null, errorList);
    }
    User subject = userManager.getUserByUsername(principal.getName());
    assertSubjectIsSysAdminOrAdmin(subject);

    // Checking licence
    userEnablementUtils.checkLicenseForUserInRole(1, roleMger.getRole(userForm.getRole()));

    return sysadminUserCreationHandler.createUser(userForm, subject).setData("Success");
  }

  /**
   * Controller method to retrieve the groups into a selected community.
   *
   * @param communityId
   * @return
   * @throws Exception
   */
  @GetMapping("/ajax/getLabGroups")
  @ResponseBody
  public AjaxReturnObject<List<GroupListResult>> getLabGroups(
      @RequestParam("communityId") Long communityId) {
    Set<Group> groups =
        communityService.getCommunityWithAdminsAndGroups(communityId).getLabGroups();
    List<GroupListResult> groupDisplay = new ArrayList<>();
    for (Group group : groups) {
      GroupListResult item = new GroupListResult(group.getId(), group.getDisplayName());
      if (!group.hasPIs()) {
        log.error(
            "Group {} [{}] does not have a PI - this should be an invariant",
            group.getUniqueName(),
            group.getId());
        item.setPiFullname("No PI set");
        item.setPiAffiliation("No PI set");
      } else {
        User pi = group.getPiusers().iterator().next();
        item.setPiFullname(pi.getFullName());
        item.setPiAffiliation(pi.getAffiliation());
      }

      item.setGroupSize(group.getMemberCount());
      groupDisplay.add(item);
    }
    return new AjaxReturnObject<List<GroupListResult>>(groupDisplay, null);
  }

  private void rejectIfNotReauthenticated(String password, BindingResult errors, User sysadmin) {
    if (!reauthenticator.reauthenticate(sysadmin, password)) {
      errors.rejectValue("sysadminPassword", "errors.reauthentication.failed");
    }
  }

  private void assertSubjectIsSysAdminOrAdmin(User subject) {
    if (!subject.hasRole(Role.SYSTEM_ROLE) && !subject.hasRole(Role.ADMIN_ROLE)) {
      throw new AuthorizationException(
          getText("system.unauthorized.userrole", new Object[] {subject.getFullName()}));
    }
  }

  private UsageListingDTO getUsageListingDTO(Principal principal, PaginationCriteria<User> pgCrit) {

    UserStatisticsDTO userStatsDTO = getUserStatisticsWithAvailableSeats();
    configureUserPagination(pgCrit);
    User sysadmin = userManager.getUserByUsername(principal.getName());
    ISearchResults<UserUsageInfo> userInfo = sysMgr.getUserUsageInfo(sysadmin, pgCrit);
    List<PaginationObject> pagination =
        PaginationUtil.generatePagination(
            userInfo.getTotalPages(),
            userInfo.getPageNumber(),
            new DefaultURLPaginator("/system/ajax/jsonList", pgCrit));

    UsageListingDTO usageListingData = new UsageListingDTO();
    usageListingData.setUserStats(userStatsDTO);
    usageListingData.setPgCrit(new PaginationCriteriaDTO(pgCrit));
    usageListingData.setUserInfo(new UserInfoListDTO(userInfo));
    usageListingData.setPagination(pagination);
    return usageListingData;
  }

  @NotNull
  private UserStatisticsDTO getUserStatisticsWithAvailableSeats() {
    final int daysToCountAsActive = 7;
    UserStatistics stats = userStatisticsManager.getUserStats(daysToCountAsActive);
    UserStatisticsDTO statsDTO = new UserStatisticsDTO(stats);

    int availableSeats = Integer.MIN_VALUE;
    // handle case where license server is unavailable RSPAC-1182
    try {
      License license = licenseService.getLicense();
      if (license != null) {
        availableSeats = license.getTotalUserSeats() - stats.getUsedLicenseSeats();
      }
    } catch (LicenseServerUnavailableException e) {
      log.warn(
          "License server not available - cannot calculate available seat count:  {}",
          e.getMessage());
    }
    statsDTO.setAvailableSeats(formatSeatCount(availableSeats));
    return statsDTO;
  }

  private String formatSeatCount(int availableSeats) {
    return availableSeats == Integer.MIN_VALUE ? UNKNOWN : "" + availableSeats;
  }

  private void configureUserPagination(PaginationCriteria<User> pgCrit) {
    pgCrit.setClazz(User.class);
    // set defaults if need be
    if (pgCrit.setOrderByIfNull("lastName")) {
      pgCrit.setSortOrder(SortOrder.ASC);
    }
  }

  @GetMapping("/createCommunity")
  public String createNewCommunityGet(Model model) {
    User subject = userManager.getAuthenticatedUserInSession();
    Community community = new Community();
    List<User> potentialAdmins = userManager.getAvailableAdminUsers();
    community.setAvailableAdmins(potentialAdmins);

    ISearchResults<Group> availableGroups = listGroups(subject);
    // this stored in the form session so as to avoid reloading each request
    model.addAttribute("communityGroups", availableGroups.getResults());
    community.setAvailableGroups(availableGroups.getResults());
    model.addAttribute(community);
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(subject, "public_sharing"));
    return SYSTEM_CREATE_COMMUNITY_VIEW;
  }

  private ISearchResults<Group> listGroups(User subject) {
    GroupSearchCriteria glf = new GroupSearchCriteria();
    glf.setGroupType(GroupType.LAB_GROUP);
    glf.setFilterByCommunity(true);
    PaginationCriteria<Group> pgCrit = PaginationCriteria.createDefaultForClass(Group.class);
    pgCrit.setResultsPerPage(Integer.MAX_VALUE); // get all
    pgCrit.setSearchCriteria(glf);

    ISearchResults<Group> availableGroups = groupManager.list(subject, pgCrit);
    return availableGroups;
  }

  @PostMapping("/createCommunity")
  public String createNewCommunityPost(
      Model model,
      @ModelAttribute Community community,
      BindingResult errors,
      SessionStatus status) {

    User subject = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(subject);

    community.createAndSetUniqueName();
    inputValidator.validate(community, communityValidator, errors);
    if (errors.hasErrors()) {
      return getCreateCommunityValidationErrorView(model, community);
    }

    for (Long id : community.getAdminIds()) {
      User admin = userManager.get(id);
      community.addAdmin(admin);
    }

    Validate.notEmpty(community.getAdmins(), "Community's admin list can't be empty");
    try {
      communityService.saveNewCommunity(community, subject);
      for (User admin : community.getAdmins()) {
        permFac.createCommunityPermissionsForAdmin(admin, community);
        userManager.save(admin);
      }
      auditService.notify(new GenericEvent(subject, community, AuditAction.CREATE));

    } catch (DataAccessException dae) {
      // probably because not unique?
      errors.reject("errors.notUnique", new Object[] {"Unique name"}, null);
      return getCreateCommunityValidationErrorView(model, community);
    }

    // does not close http session, just signals that SessionAttributes
    // declared by this class can now be removed.
    status.setComplete();
    return REDIRECT_SYSTEM_COMMUNITY_LIST;
  }

  @SuppressWarnings("unchecked")
  private String getCreateCommunityValidationErrorView(Model model, Community community) {
    List<User> potentialAdmins = userManager.getAvailableAdminUsers();
    community.setAvailableAdmins(potentialAdmins);
    community.setAvailableGroups((List<Group>) model.asMap().get("communityGroups"));
    return SYSTEM_CREATE_COMMUNITY_VIEW;
  }

  @GetMapping("/ajax/runAs")
  public String runAs(Model model) {
    RunAsUserCommand cmnd = new RunAsUserCommand();
    model.addAttribute(cmnd);
    model.addAttribute("isVerificationPwdRequired", properties.isSSO());

    return "system/runAsUserDlg";
  }

  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"sysadminPassword"})
  @PostMapping("/ajax/runAs")
  public String runAs(
      Model model,
      HttpSession session,
      @ModelAttribute RunAsUserCommand runAsUserCmnd,
      BindingResult errors) {

    User adminUser = userManager.getAuthenticatedUserInSession();
    ValidationUtils.rejectIfEmpty(
        errors, "sysadminPassword", "errors.required", new Object[] {"password"});
    ValidationUtils.rejectIfEmpty(
        errors, "runAsUsername", "errors.required", new Object[] {"username"});
    String[] users = User.getUsernameesFromMultiUser(runAsUserCmnd.getRunAsUsername());
    if (ArrayUtils.isEmpty(users)) {
      errors.rejectValue("runAsUsername", "system.runAs.invalidusernameformat.msg", null);
    }
    rejectIfNotReauthenticated(runAsUserCmnd.getSysadminPassword(), errors, adminUser);
    if (errors.hasErrors()) {
      return "system/runAsUserDlg";
    }
    String targetUsername = users[0];
    if (getCurrentActiveUsers().getActiveUsers().contains(targetUsername)) {
      errors.reject("system.user2pi.userIsActive", new Object[] {targetUsername}, null);
    }
    if (errors.hasErrors()) {
      return "system/runAsUserDlg";
    }
    // can't impersonate a user outside the community
    if (!userPermissionUtils.isTargetUserValidForSubjectRole(adminUser, targetUsername)) {
      errors.reject("system.runAs.userNotInCommunity", new Object[] {targetUsername}, null);
    }
    User targetUser = null;
    try {
      targetUser = userManager.getUserByUsername(targetUsername);
    } catch (DataAccessException dae) {
      errors.reject("errors.username", null, null);
    }
    if (errors.hasErrors()) {
      return "system/runAsUserDlg";
    }

    permissionUtils.doRunAs(session, adminUser, targetUser);
    updateSessionUser(targetUser, session);
    getCurrentActiveUsers().addUser(targetUser.getUsername(), session);
    if (!runAsUserCmnd.isIncognito()) {
      notifyByEmailWhenAdminOperateAs(adminUser, targetUsername);
    }
    model.addAttribute("completed", Boolean.TRUE);
    return "system/runAsUserDlg";
  }

  @GetMapping("/ajax/listRunAsUsers")
  @ResponseBody
  public AjaxReturnObject<List<UserBasicInfo>> getAllUsers(
      Principal principal, @RequestParam(value = "term") String term) {

    User subject = userManager.getAuthenticatedUserInSession();
    List<User> users = new ArrayList<>();
    if (subject.hasRole(Role.SYSTEM_ROLE)) {
      users = userManager.getAll();
    } else if (subject.hasRole(Role.ADMIN_ROLE)) {
      users = userManager.getAllUsersInAdminsCommunity(principal.getName());
    }
    if (StringUtils.isNotEmpty(term)) {
      List<User> matchingUsers = userManager.searchUsers(term);
      users.retainAll(matchingUsers);
    }

    List<UserBasicInfo> userInfos = new ArrayList<>();
    for (User user : users) {
      userInfos.add(user.toBasicInfo());
      if (userInfos.size() > 10) {
        break; // autocomplete displays only 10 users, so no need for more (RSPAC-1252)
      }
    }
    return new AjaxReturnObject<List<UserBasicInfo>>(userInfos, null);
  }

  /**
   * Gets a community view with edit permission based on admin's permissions
   *
   * @param model
   * @param id
   * @return
   */
  @GetMapping("/community/{id}")
  public String getCommunityForEdit(Model model, @PathVariable Long id) {
    User authUser = userManager.getAuthenticatedUserInSession();
    Community comm = communityService.getCommunityWithAdminsAndGroups(id);
    model.addAttribute("community", comm);

    // we want display default community groups in 'add group' scenario (RSPAC-2200)
    if (!Community.DEFAULT_COMMUNITY_ID.equals(id)) {
      Community defaultComm =
          communityService.getCommunityWithAdminsAndGroups(Community.DEFAULT_COMMUNITY_ID);
      model.addAttribute("defaultCommunity", defaultComm);
    }

    model.addAttribute("view", true); // Flag to display view mode not edit mode
    model.addAttribute(
        "canEdit", permissionUtils.isPermitted(comm, PermissionType.WRITE, authUser));
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(authUser, "public_sharing"));
    return "system/community";
  }

  private void notifyByEmailWhenAdminOperateAs(User admin, String username) {
    User runAs = userManager.getUserByUsername(username);
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("runAs", runAs);
    velocityModel.put("systemUser", admin);
    velocityModel.put("htmlPrefix", properties.getServerUrl());
    EmailContent content =
        strictEmailContentGenerator.generatePlainTextAndHtmlContent(
            "adminRunningAsUserNotification.vm", velocityModel);
    emailer.sendHtmlEmail(
        "RSpace admin is using your account", content, toList(runAs.getEmail()), null);
  }
}
