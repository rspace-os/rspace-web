package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toList;

import com.axiope.userimport.IPostUserSignup;
import com.researchspace.Constants;
import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.Role;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.RoleManager;
import com.researchspace.service.SignupCaptchaVerifier;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserExistsException;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy;
import com.researchspace.webapp.filter.RemoteUserRetrievalPolicy.RemoteUserAttribute;
import com.researchspace.webapp.filter.SSOShiroFormAuthFilterExt;
import java.io.UnsupportedEncodingException;
import javax.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/** Controller to signup new users. */
@Controller("signupController")
@RequestMapping("/signup")
@BrowserCacheAdvice
public class SignupController extends BaseController {

  public static final String SIGNUP_URL = "/signup";

  public static final String CLOUD_SIGNUP_ACCOUNT_ACTIVATION_FAIL_URL =
      "cloud/signup/accountActivationFail";

  private @Autowired RoleManager roleManager;

  @Autowired
  @Setter(AccessLevel.PACKAGE) // for testing
  private RemoteUserRetrievalPolicy remoteUserPolicy;

  private @Autowired UserValidator userValidator;
  private @Autowired SignupCaptchaVerifier captchaVerifier;
  private @Autowired UserEnablementUtils userEnablementUtils;

  @Autowired
  @Qualifier("loginPasswordResetByEmailHandler")
  private PasswordResetByEmailHandlerBase passwordResetEmailHandler;

  @Autowired private UsernameReminderByEmailHandler usernameReminderByEmailHandler;

  @Autowired
  @Qualifier("manualPolicy")
  private ISignupHandlerPolicy manualSignupPolicy;

  @Autowired
  @Qualifier("emailBroadcast")
  private EmailBroadcast emailer;

  @Autowired
  @Qualifier("postSignup")
  private IPostUserSignup postSignup;

  @Value("${user.signup.acceptedDomains}")
  @Setter(AccessLevel.PACKAGE) // for testing
  private String acceptedSignupDomains;

  @Value("${deployment.sso.recodeIncomingFirstNameLastNameToUtf8}")
  @Setter(AccessLevel.PACKAGE) // for testing
  private Boolean deploymentSsoRecodeNamesToUft8;

  @Value("${deployment.sso.signup.username.suffixToReplace}")
  @Setter(AccessLevel.PACKAGE) // for testing
  private String deploymentSsoSignupUsernameSuffixToReplace;

  @Value("${deployment.sso.signup.username.suffixReplacement}")
  @Setter(AccessLevel.PACKAGE) // for testing
  private String deploymentSsoSignupUsernameSuffixReplacement;

  public SignupController() {
    setCancelView("redirect:login");
    setSuccessView("redirect:workspace");
  }

  @ModelAttribute
  @GetMapping
  @DeploymentProperty(value = DeploymentPropertyType.USER_SIGNUP_ENABLED)
  public ModelAndView getSignupForm(
      ModelAndView model,
      HttpServletRequest request,
      @RequestParam(value = "token", required = false) String token) {

    userEnablementUtils.checkLicenseForUserInRole(1, Role.USER_ROLE);
    User user = new User();
    if (token != null && properties.isCloud()) {
      TokenBasedVerification tokenObject = userManager.getUserVerificationToken(token);
      if (tokenObject == null
          || !tokenObject.isValidLink(token, TokenBasedVerificationType.VERIFIED_SIGNUP)) {
        model.setViewName(CLOUD_SIGNUP_ACCOUNT_ACTIVATION_FAIL_URL);
        return model;
      }
      user.setToken(token);
      user.setEmail(tokenObject.getEmail());
    }
    if (properties.isSSO()) {
      String remoteUser = remoteUserPolicy.getRemoteUser(request);
      if (!isSsoSignupAllowed(remoteUser)) {
        model.setViewName("redirect:" + SSOShiroFormAuthFilterExt.SSOINFO_URL);
        return model;
      }
      setUsernameAndAliasFromRemoteUserInSsoMode(user, remoteUser);
      // if these aren't set remotely they will set empty string.
      // if they are set, then the signup form will be pre-populated
      user.setEmail(getEmailFromRemote(request));
      user.setLastName(getLastNameFromRemote(request));
      user.setFirstName(getFirstNameFromRemote(request));
      // RSPAC-2588 optional param from SSO deciding if PI role should be selectable
      model.addObject("isAllowedPiRole", getIsAllowedPiRoleFromRemote(request));
    }
    user.setRole(Constants.USER_ROLE);
    model.addObject(user);
    model.setViewName("signup");
    return model;
  }

  protected User setUsernameAndAliasFromRemoteUserInSsoMode(User user, String remoteUser) {
    if (StringUtils.isNotEmpty(deploymentSsoSignupUsernameSuffixToReplace)
        && remoteUser.endsWith(deploymentSsoSignupUsernameSuffixToReplace)) {
      /* RSDEV-669 - replace suffix in main username, set original SSO username as alias */
      String newUsername =
          remoteUser.substring(
                  0, remoteUser.length() - deploymentSsoSignupUsernameSuffixToReplace.length())
              + deploymentSsoSignupUsernameSuffixReplacement;
      user.setUsername(newUsername);
      user.setUsernameAlias(remoteUser);
    } else {
      user.setUsername(remoteUser);
    }
    return user;
  }

  private String getEmailFromRemote(HttpServletRequest req) {
    String emailString =
        remoteUserPolicy.getOtherRemoteAttributes(req).get(RemoteUserAttribute.EMAIL);
    return emailString == null ? "" : emailString;
  }

  protected String getFirstNameFromRemote(HttpServletRequest req) {
    String rc = remoteUserPolicy.getOtherRemoteAttributes(req).get(RemoteUserAttribute.FIRST_NAME);
    return rc == null ? "" : ensureUtf8EncodingForSAMLAttributeValue(rc);
  }

  protected String getLastNameFromRemote(HttpServletRequest req) {
    String rc = remoteUserPolicy.getOtherRemoteAttributes(req).get(RemoteUserAttribute.LAST_NAME);
    return rc == null ? "" : ensureUtf8EncodingForSAMLAttributeValue(rc);
  }

  private boolean getIsAllowedPiRoleFromRemote(HttpServletRequest req) {
    String rc =
        remoteUserPolicy.getOtherRemoteAttributes(req).get(RemoteUserAttribute.IS_ALLOWED_PI_ROLE);
    return "true".equals(rc);
  }

  private String ensureUtf8EncodingForSAMLAttributeValue(String rc) {
    if (deploymentSsoRecodeNamesToUft8) {
      try {
        String recodedFromIso = new String(rc.getBytes("ISO-8859-1"), "UTF-8");
        if (recodedFromIso.length() < rc.length()) {
          return recodedFromIso;
        }
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
    return rc;
  }

  boolean isSsoSignupAllowed(String remoteUser) {
    if (properties.isUserSignup()) {
      // if blank, allow all domains
      if (StringUtils.isEmpty(acceptedSignupDomains)
          || remoteUser.endsWith(acceptedSignupDomains)) {
        return true;
      }
      // allow comma-separated list of allowed domains
      return toList(acceptedSignupDomains.split(",")).stream()
          .map(String::trim)
          .anyMatch(remoteUser::endsWith);
    }
    return false;
  }

  /** Handles signup submission POST */
  @PostMapping
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"password, confirmPassword"})
  @DeploymentProperty(value = DeploymentPropertyType.USER_SIGNUP_ENABLED)
  public String onSubmit(User user, BindingResult errors, HttpServletRequest request) {

    if (request.getParameter("cancel") != null) {
      return getCancelView();
    }
    log.info("Signing up user {}", user.getUsername());
    // we verify captcha 1st if it is enabled
    if ("true".equals(properties.getSignupCaptchaEnabled())) {
      String verificationResult = captchaVerifier.verifyCaptchaFromRequest(request);
      if (!SignupCaptchaVerifier.CAPTCHA_OK.equals(verificationResult)) {
        errors.rejectValue("captcha", verificationResult);
        return returnToSignupPage(user);
      }
    }

    user.setEnabled(true);
    userValidator.validate(user, errors);
    if (errors.hasErrors()) {
      return returnToSignupPage(user);
    }

    if (properties.isSSO()) {
      String remoteUser = remoteUserPolicy.getRemoteUser(request);
      String pwd = remoteUserPolicy.getPassword();
      if (!isSsoSignupAllowed(remoteUser)) {
        return returnToSignupPage(user);
      }
      setUsernameAndAliasFromRemoteUserInSsoMode(user, remoteUser);
      user.setPassword(pwd);
      user.setConfirmPassword(pwd);
    }
    addRole(user);

    String originalPwd = user.getPassword();
    User savedUser;
    try {
      // final check the license is OK BEFORE saving the user.
      userEnablementUtils.checkLicenseForUserInRole(1, roleManager.getRole(user.getRole()));
      savedUser = manualSignupPolicy.saveUser(user, request);
    } catch (UserExistsException e) {
      // if that's username problem we may give user better message
      if (e.isExistingUsername()) {
        errors.rejectValue(
            "username",
            "errors.existing.username",
            new Object[] {user.getUsername()},
            "duplicate username");
      } else if (e.isExistingUsernameAlias()) {
        errors.rejectValue(
            "username",
            "errors.existing.usernameAlias",
            new Object[] {user.getUsername()},
            "duplicate username alias");
      } else {
        errors.rejectValue(
            "username",
            "errors.existing.user",
            new Object[] {user.getUsername(), user.getEmail()},
            "duplicate user");
      }
      return returnToSignupPage(user);
    }

    if (properties.isCloud()) {
      organisationManager.checkAndSaveNonApprovedOrganisation(user);
    }
    postSignup.postUserCreate(savedUser, request, originalPwd);

    return postSignup.getRedirect(savedUser);
  }

  private void addRole(User user) {
    user.setRole(Constants.USER_ROLE);
    user.addRole(roleManager.getRole(Constants.USER_ROLE));
  }

  private String returnToSignupPage(User user) {
    user.setPassword(user.getConfirmPassword());
    return "signup";
  }

  /**
   * Posts an initial request to change a password.<br>
   * This method stores the request and notifies the requester by email
   */
  @PostMapping("/passwordResetRequest")
  public ModelAndView requestPasswordReset(
      HttpServletRequest request, @RequestParam("email") String email) {
    passwordResetEmailHandler.sendChangeCredentialsEmail(request, email);
    ModelAndView mav = new ModelAndView("passwordReset/resetPasswordRequestSent");
    mav.addObject("email", email);
    mav.addObject("passwordType", PasswordType.LOGIN_PASSWORD);
    return mav;
  }

  /** Submits a token to access the password change dialog */
  @GetMapping("/passwordResetReply")
  public ModelAndView getPasswordResetPage(@RequestParam("token") String token) {
    return passwordResetEmailHandler
        .getResetPage(token)
        .addObject("passwordType", PasswordType.LOGIN_PASSWORD);
  }

  @PostMapping("/passwordResetReply")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"password", "confirmPassword"})
  public ModelAndView submitPasswordResetPage(
      @ModelAttribute PasswordResetCommand passwordResetCommand,
      BindingResult errors,
      HttpServletRequest request)
      throws Exception {
    return passwordResetEmailHandler
        .submitResetPage(passwordResetCommand, errors, request)
        .addObject("passwordType", PasswordType.LOGIN_PASSWORD);
  }

  /** Posts an email with username information. */
  @PostMapping("/usernameReminderRequest")
  public ModelAndView requestUsernameReminder(
      HttpServletRequest request, @RequestParam("email") String email) {

    usernameReminderByEmailHandler.sendUsernameReminderEmail(request, email);

    return (new ModelAndView("usernameReminder/remindUsernameEmailSent")).addObject("email", email);
  }
}
