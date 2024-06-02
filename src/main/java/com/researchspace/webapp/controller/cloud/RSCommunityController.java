package com.researchspace.webapp.controller.cloud;

import static com.researchspace.model.dtos.CreateCloudGroupValidator.EMAIL_PATTERN;
import static java.util.stream.Collectors.toList;

import com.axiope.userimport.IPostUserSignup;
import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.ProductType;
import com.researchspace.model.Role;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.dto.UserBasicInfo;
import com.researchspace.model.dtos.CreateCloudGroup;
import com.researchspace.model.dtos.CreateCloudGroupValidator;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.views.GroupListResult;
import com.researchspace.service.cloud.CloudGroupManager;
import com.researchspace.service.cloud.CloudNotificationManager;
import com.researchspace.service.cloud.CommunityUserManager;
import com.researchspace.webapp.controller.AjaxReturnObject;
import com.researchspace.webapp.controller.BaseController;
import com.researchspace.webapp.controller.Product;
import com.researchspace.webapp.controller.WorkspaceController;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller("cloudController")
@Product(ProductType.COMMUNITY)
@RequestMapping("/cloud")
public class RSCommunityController extends BaseController {

  public static final String EMAIL_CHANGE_TOKEN_ATT_NAME = "emailChangeToken";

  static final String CLOUD_SIGNUP_ACTIVATION_FAIL_VIEW = "cloud/signup/accountActivationFail";

  private @Autowired WorkspaceController workspaceController;
  private @Autowired CommunityUserManager cloudUserManager;
  private @Autowired CloudGroupManager cloudGroupManager;
  private @Autowired CloudNotificationManager cloudNotificationManager;
  private @Autowired CreateCloudGroupValidator cloudCloudGroupValidator;
  private @Autowired AnalyticsManager analyticsManager;

  @Autowired
  @Qualifier("communityPostSignup")
  private IPostUserSignup postUserSignup;

  public void setPostUserSignup(IPostUserSignup postUserSignup) {
    this.postUserSignup = postUserSignup;
  }

  /**
   * @param principal
   * @param model
   * @return
   */
  @RequestMapping(method = RequestMethod.GET, value = "/group/new")
  public String createCloudGroup(Model model) {

    CreateCloudGroup createCloudGroupConfig = new CreateCloudGroup();
    model.addAttribute("createCloudGroupConfig", createCloudGroupConfig);
    return "admin/cloud/createCloudGroupForm";
  }

  /**
   * On Submit create.
   *
   * @return AjaxReturnObject<Boolean> on successful processing.
   */
  @PostMapping("/createCloudGroup2")
  public @ResponseBody AjaxReturnObject<Boolean> createCloudGroup2(
      @RequestBody CreateCloudGroup createCloudGroup,
      BindingResult errors,
      HttpServletRequest request) {

    User creator = userManager.getAuthenticatedUserInSession();
    createCloudGroup.setSessionUser(creator);
    cloudCloudGroupValidator.validate(createCloudGroup, errors);
    if (errors.hasErrors()) {
      ErrorList el = new ErrorList();
      inputValidator.populateErrorList(errors, el);
      return new AjaxReturnObject<Boolean>(null, el);
    }

    List<String> listEmails = Arrays.asList(createCloudGroup.getEmails());

    // subject wants to be pi
    if (!createCloudGroup.isNomination(creator)) {
      if (!creator.hasRole(Role.PI_ROLE)) {
        creator = cloudGroupManager.promoteUserToPI(creator, creator);
      }
      Group newLabGroup =
          cloudGroupManager.createAndSaveGroup(
              createCloudGroup.getGroupName(), creator, creator, GroupType.LAB_GROUP, creator);

      if (!listEmails.isEmpty() && !listEmails.get(0).isEmpty()) {
        List<User> usersList = cloudUserManager.createInvitedUserList(listEmails);
        newLabGroup.setMemberString(usersList.stream().map(User::getUsername).collect(toList()));
        permissionUtils.refreshCache();
        cloudNotificationManager.sendJoinGroupRequest(creator, newLabGroup);

        for (User invitedUser : usersList) {
          cloudNotificationManager.sendJoinGroupInvitationEmail(
              creator, invitedUser, newLabGroup, request);
        }
      }

    } else { // we're nominating someone  to be PI

      User invitedPI = cloudUserManager.createInvitedUser(createCloudGroup.getPiEmail());
      cloudNotificationManager.sendCreateGroupRequest(
          creator, invitedPI, listEmails, createCloudGroup.getGroupName());

      cloudNotificationManager.sendPIInvitationEmail(
          creator, invitedPI, createCloudGroup.getGroupName(), request);
    }

    return new AjaxReturnObject<Boolean>(Boolean.TRUE, null);
  }

  /**
   * @param groupId
   * @param emails
   * @param request
   * @return
   */
  @ResponseBody
  @PostMapping("/inviteCloudUser")
  public AjaxReturnObject<List<String>> inviteCloudUser(
      @RequestParam(value = "groupId") long groupId,
      @RequestParam(value = "emails[]") String[] emails,
      HttpServletRequest request) {

    ErrorList error = new ErrorList();
    User creator = userManager.getAuthenticatedUserInSession();
    Group group = cloudGroupManager.getCloudGroup(groupId);

    List<String> listEmails = Arrays.asList(emails);
    if (listEmails.isEmpty()) {
      error.addErrorMsg("Email list is empty !");
      return new AjaxReturnObject<List<String>>(null, error);
    }

    for (String s : emails) {
      if (!isEmailValid(s)) {
        error.addErrorMsg("Incorrect email");
        return new AjaxReturnObject<List<String>>(null, error);
      }
    }

    List<User> usersList = cloudUserManager.createInvitedUserList(listEmails);
    group.setMemberString(usersList.stream().map(User::getUsername).collect(toList()));
    cloudNotificationManager.sendJoinGroupRequest(creator, group);

    List<String> emailRC = new ArrayList<>();
    for (User invitedUser : usersList) {
      cloudNotificationManager.sendJoinGroupInvitationEmail(creator, invitedUser, group, request);
      emailRC.add(invitedUser.getEmail());
    }
    return new AjaxReturnObject<List<String>>(emailRC, null);
  }
  ;

  /**
   * @param email
   * @return
   */
  boolean isEmailValid(String email) {
    Matcher matcher = EMAIL_PATTERN.matcher(email);
    return matcher.matches() && (email.length() < User.DEFAULT_MAXFIELD_LEN);
  }

  /**
   * Gets public user info based on search term
   *
   * @param term
   * @param model
   * @param request
   * @param reponse
   * @return
   * @throws Exception
   */
  @ResponseBody
  @GetMapping("/ajax/searchPublicUserInfoList")
  public AjaxReturnObject<List<UserBasicInfo>> searchPublicUserInfoList(
      @RequestParam(value = "term", required = true) String term) {
    term = SecureStringUtils.removeWildCards(term);
    List<UserBasicInfo> userInfos = null;
    try {
      userInfos = userManager.searchPublicUserInfoList(term);
    } catch (IllegalArgumentException e) {
      if (e.getMessage().contains("must be at least 3 characters")) {
        ErrorList error =
            ErrorList.of(getText("errors.minlength", new String[] {"Search term", "3"}));
        return new AjaxReturnObject<>(null, error);
      } else {
        throw e;
      }
    }
    return new AjaxReturnObject<>(userInfos, null);
  }

  @ResponseBody
  @GetMapping("/ajax/searchGroupList")
  public AjaxReturnObject<List<GroupListResult>> searchGroupList(
      @RequestParam(value = "term", required = true) String term) {

    User userInSession = userManager.getAuthenticatedUserInSession();
    final int minLength = 3;

    if (term.length() < minLength) {
      ErrorList error =
          ErrorList.of(getText("errors.minlength", new String[] {"Search term", "3"}));
      return new AjaxReturnObject<List<GroupListResult>>(null, error);
    }

    List<GroupListResult> groupList = new ArrayList<GroupListResult>();
    List<Group> groups = cloudGroupManager.searchGroups(term);
    Set<Group> internalGroups = userInSession.getGroups();

    for (Group group : groups) {
      if (!internalGroups.contains(group)) {
        User pi = group.getPiusers().iterator().next();
        String piFullname = pi.getFullName();
        String piAffiliation = pi.getAffiliation();
        GroupListResult r = new GroupListResult(group.getId(), group.getDisplayName());
        r.setPiFullname(piFullname);
        r.setPiAffiliation(piAffiliation);
        groupList.add(r);
      }
    }
    return new AjaxReturnObject<List<GroupListResult>>(groupList, null);
  }

  /**
   * Submits a token to activate account
   *
   * @param token
   * @return
   */
  @GetMapping("/verifysignup")
  @Product(value = {ProductType.COMMUNITY})
  public ModelAndView getSignupVerificationPage(@RequestParam("token") String token) {

    final int maxWidth = 255;
    token = StringUtils.abbreviate(token, maxWidth);
    TokenBasedVerification change = userManager.getUserVerificationToken(token);
    if (change != null && change.isValidLink(token, TokenBasedVerificationType.VERIFIED_SIGNUP)) {
      ModelAndView mav = new ModelAndView("cloud/signup/cloudAcceptSignupForm");
      mav.addObject("token", token);
      return mav;
    }
    return new ModelAndView(CLOUD_SIGNUP_ACTIVATION_FAIL_VIEW);
  }

  /**
   * Submits a token to activate account
   *
   * @param token
   * @return Either success or completion view
   */
  @PostMapping("/verifysignup")
  public String postSignupVerificationPage(
      @RequestParam("token") String token, HttpServletRequest request) {

    final int maxWidth = 255;
    token = StringUtils.abbreviate(token, maxWidth);
    User activated = cloudUserManager.activateUser(token);
    if (activated != null) {
      analyticsManager.userSignedUp(activated, false, request);
      return "redirect:/cloud/signup/accountActivationComplete";
    } else {
      return CLOUD_SIGNUP_ACTIVATION_FAIL_VIEW;
    }
  }

  @GetMapping("/verifyEmailChange")
  @Product(value = {ProductType.COMMUNITY})
  public ModelAndView getEmailChangeVerificationPage(
      @RequestParam("token") String token, Model model, Principal p) {

    final int maxWidth = 255;
    token = StringUtils.abbreviate(token, maxWidth);
    TokenBasedVerification changeEmailToken = userManager.getUserVerificationToken(token);

    String errorMsg = null;
    if (changeEmailToken == null
        || !changeEmailToken.isValidLink(token, TokenBasedVerificationType.EMAIL_CHANGE)) {
      errorMsg = "token.verification.fail.help2";
    } else {
      String tokenUsername = changeEmailToken.getUser().getUsername();
      if (!tokenUsername.equals(p.getName())) {
        errorMsg = "token.verification.fail.wrong.user";
      } else if (CollectionUtils.isNotEmpty(
          userManager.getUserByEmail(changeEmailToken.getEmail()))) {
        errorMsg = "token.verification.email.change.fail.already.taken";
      }
    }

    if (errorMsg != null) {
      model.addAttribute("errorMsg", getText(errorMsg));
      return new ModelAndView("cloud/verifyEmailChange/emailChangeFailed");
    }

    ModelAndView mav = new ModelAndView("cloud/verifyEmailChange/verifyEmailChange");
    mav.addObject(EMAIL_CHANGE_TOKEN_ATT_NAME, changeEmailToken);
    return mav;
  }

  public static String getEmailChangeTokenAttName() {
    return EMAIL_CHANGE_TOKEN_ATT_NAME;
  }

  public void setCloudUserManager(CommunityUserManager cloudUserManager) {
    this.cloudUserManager = cloudUserManager;
  }

  /**
   * Submits a token to confirm email change
   *
   * @param token
   */
  @PostMapping("/verifyEmailChange")
  public String postEmailChangeConfirmation(
      @RequestParam("token") String token, HttpSession session, Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    boolean result = cloudUserManager.emailChangeConfirmed(token, user);
    if (result) {
      // get updated user
      user = userManager.getUserByUsername(principal.getName(), true);
      updateSessionUser(user, session);
    }
    return "redirect:/cloud/verifyEmailChange/"
        + (result ? "emailChangeConfirmed" : "emailChangeFailed");
  }

  @GetMapping("/resendConfirmationEmail/awaitingEmailConfirmation")
  public ModelAndView getAwaitingEmailConfirmation(
      @RequestParam("email") String email, HttpServletRequest request, Model model) {
    model.addAttribute("email", email);
    return new ModelAndView("cloud/resendConfirmationEmail/awaitingEmailConfirmation");
  }

  @GetMapping("/signup/cloudSignupConfirmation")
  public ModelAndView getCloudSignupConfirmation(
      @RequestParam("email") String email, HttpServletRequest request, Model model) {
    model.addAttribute("email", email);
    return new ModelAndView("cloud/signup/cloudSignupConfirmation");
  }

  @PostMapping("/resendConfirmationEmail/resend")
  public String resendEmailConfirmation(
      @RequestParam("email") String email, HttpServletRequest request) {
    List<User> users = userManager.getUserByEmail(email);
    if (users.isEmpty()) {
      return "redirect:/cloud/resendConfirmationEmail/resendFailure";
    }
    User user = users.get(0);
    if (!user.isAccountAwaitingEmailConfirmation()) {
      return "redirect:/cloud/resendConfirmationEmail/resendFailure";
    }

    postUserSignup.postUserCreate(user, request, null);
    return "redirect:/cloud/resendConfirmationEmail/resendSuccess";
  }

  /**
   * @param shareConfig
   * @param principal
   * @param request
   * @return
   */
  @ResponseBody
  @PostMapping("/ajax/shareRecord")
  public AjaxReturnObject<SharingResult> shareRecord(
      @RequestBody ShareConfigCommand shareConfig,
      Principal principal,
      HttpServletRequest request) {

    ErrorList error = new ErrorList();
    User sharer = userManager.getUserByUsername(principal.getName(), true);
    Set<Long> sharedIds = new LinkedHashSet<>();

    /*
     * STANDARD SHARE with internal users or groups - delegated to WorkspaceController.shareRecord()
     */
    // let's find internal users and groups selected for share,
    List<ShareConfigElement> internalUserOrGroupElems = new ArrayList<ShareConfigElement>();
    for (ShareConfigElement elem : shareConfig.getValues()) {
      if (elem.getGroupid() != null || elem.getUserId() != null) {
        internalUserOrGroupElems.add(elem);
      }
    }
    boolean internalUserOrGroupsFound = !internalUserOrGroupElems.isEmpty();
    if (internalUserOrGroupsFound) {
      ShareConfigCommand internalShareConfig = new ShareConfigCommand();
      internalShareConfig.setIdsToShare(shareConfig.getIdsToShare());
      internalShareConfig.setValues(internalUserOrGroupElems.toArray(new ShareConfigElement[0]));

      AjaxReturnObject<SharingResult> internalShareResult =
          workspaceController.shareRecord(internalShareConfig, principal);
      sharedIds.addAll(internalShareResult.getData().getSharedIds());
      error.addErrorList(internalShareResult.getError());
    }

    /*
     * CLOUD SPECIFIC SHARE with external user through email or with external group
     */
    for (Long toShareId : shareConfig.getIdsToShare()) {
      for (ShareConfigElement elem : shareConfig.getValues()) {
        String email = elem.getEmail();
        if (email != null) {
          if (!isEmailValid(email)) {
            error.addErrorMsg("Incorrect email");
            return new AjaxReturnObject<>(null, error);
          }
          User invitedUser = cloudUserManager.createInvitedUser(email);
          sendRequestAndEmail(request, sharer, toShareId, elem, invitedUser);
          sharedIds.add(toShareId);
        } else {
          Long externalGroupId = elem.getExternalGroupId();
          if (externalGroupId != null) {
            Group externalGroup = cloudGroupManager.getCloudGroup(externalGroupId);
            for (User externalGroupMember : externalGroup.getMembers()) {
              sendRequestAndEmail(request, sharer, toShareId, elem, externalGroupMember);
            }
            sharedIds.add(toShareId);
          }
        }
      }
    }
    SharingResult sharingResult = new SharingResult(new ArrayList<>(sharedIds), new ArrayList<>());
    return new AjaxReturnObject<>(sharingResult, error);
  }

  /**
   * @param request
   * @param sharing
   * @param id
   * @param e
   * @param invitedUser
   */
  private void sendRequestAndEmail(
      HttpServletRequest request, User sharing, Long id, ShareConfigElement e, User invitedUser) {

    cloudNotificationManager.sendShareRecordRequest(sharing, invitedUser, id, e.getOperation());

    String name = getSharedRecordName(sharing, id);
    cloudNotificationManager.sendShareRecordInvitationEmail(sharing, invitedUser, name, request);
  }

  /**
   * @param sharing
   * @param id
   * @return
   */
  private String getSharedRecordName(User sharing, Long id) {
    String name;
    if (recordManager.exists(id)) {
      name = recordManager.get(id).getName();
    } else {
      name = folderManager.getNotebook(id).getName();
    }
    return name;
  }
}
