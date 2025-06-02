package com.researchspace.webapp.controller;

import static com.researchspace.webapp.controller.WorkspaceController.ROOT_URL;
import static com.researchspace.webapp.filter.RemoteUserRetrievalPolicy.SSO_DUMMY_PASSWORD;

import com.axiope.userimport.IPostUserSignup;
import com.researchspace.Constants;
import com.researchspace.auth.AccountEnabledAuthorizer;
import com.researchspace.auth.LoginHelper;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.googleauth.ExternalAuthTokenVerifier;
import com.researchspace.googleauth.ExternalProfile;
import com.researchspace.model.ProductType;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserEnablementUtils;
import com.researchspace.service.UserExistsException;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import lombok.Setter;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller to signup and login new users that have signed up or signed in via an OAuth mechanism
 * in the browser.
 *
 * <p>These paths should be unauthenticated so users can get access to login and signup.
 *
 * <p><strong> This should only be available for Community edition.</strong>
 */
@Controller("externalAuth")
@RequestMapping("/externalAuth")
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
@Product(ProductType.COMMUNITY)
public class ExternalAuthController extends BaseController {

  @Autowired
  @Qualifier("defaultPostSignUp")
  private IPostUserSignup defaultPostSignUp;

  @Autowired @Setter private UserEnablementUtils userEnablementUtils;

  @Autowired private RoleManager roleManager;

  @Autowired
  @Qualifier("externalPolicy")
  private ISignupHandlerPolicy externalPolicy;

  @Autowired private ExternalAuthTokenVerifier externalAuthVerifier;

  @Autowired
  @Qualifier("postOAuthLoginHelper")
  private LoginHelper loginHelper;

  public ExternalAuthController() {
    setCancelView("redirect:login");
    setSuccessView("redirect:workspace");
  }

  @PostMapping(path = "ajax/signup")
  @IgnoreInLoggingInterceptor(ignoreAllRequestParams = true)
  public @ResponseBody AjaxReturnObject<String> externalSignup(
      @RequestParam("idTokenString") String idTokenString,
      @RequestParam("clientId") String clientId,
      HttpServletRequest request)
      throws UserExistsException {
    Optional<ExternalProfile> profile = externalAuthVerifier.verify(clientId, idTokenString);
    if (profile.isPresent()) {
      User newUser = createUserFromProfile(profile.get());
      log.info("{}", profile);
      userEnablementUtils.checkLicenseForUserInRole(1, roleManager.getRole(newUser.getRole()));
      newUser = externalPolicy.saveUser(newUser, request);
      defaultPostSignUp.postUserCreate(newUser, request, SSO_DUMMY_PASSWORD);
      return new AjaxReturnObject<String>(
          defaultPostSignUp.getRedirect(newUser).replace("redirect:", "/"), null);
    } else {
      log.warn("No profile");
      return new AjaxReturnObject<String>(
          null,
          ErrorList.createErrListWithSingleMsg("Couldn't create or sign in, idToken was invalid."));
    }
  }

  /**
   * @param idTokenString
   * @param clientId
   * @param request
   * @return The post-login redirect URL
   */
  @PostMapping(path = "ajax/login")
  @IgnoreInLoggingInterceptor(ignoreAllRequestParams = true)
  public @ResponseBody AjaxReturnObject<String> externalLogin(
      @RequestParam("idTokenString") String idTokenString,
      @RequestParam("clientId") String clientId,
      HttpServletRequest request) {
    Optional<ExternalProfile> profile = externalAuthVerifier.verify(clientId, idTokenString);
    if (profile.isPresent()) {
      User newUser = createUserFromProfile(profile.get());
      log.info("{}", profile);
      // we have no account, reject
      List<User> existingUser = userManager.getUserByEmail(newUser.getEmail());
      if (existingUser.isEmpty()) {
        SECURITY_LOG.warn(
            "Login attempt for unknown email [{}] from {}",
            newUser.getEmail(),
            RequestUtil.remoteAddr(request));
        String message =
            String.format("No account found for email [%s], please sign up", newUser.getEmail());
        throw new IllegalStateException(message);
      }
      User toLogin = existingUser.get(0);
      if (toLogin.isLoginDisabled()) {
        return new AjaxReturnObject<String>(AccountEnabledAuthorizer.REDIRECT_FOR_DISABLED, null);
      }
      loginHelper.login(toLogin, "", request);
      return new AjaxReturnObject<String>(ROOT_URL, null);
    } else {
      log.warn("No profile retrieved");
      return new AjaxReturnObject<String>(
          null,
          ErrorList.createErrListWithSingleMsg("Couldn't create or sign in, idToken was invalid."));
    }
  }

  private User createUserFromProfile(ExternalProfile profile) {
    User user = new User();
    user.setEmail(profile.getEmail());
    user.setFirstName(profile.getGivenName());
    user.setLastName(profile.getFamilyName());
    user.setUsername(profile.getEmail());
    user.setPassword(SSO_DUMMY_PASSWORD);
    user.setConfirmPassword(SSO_DUMMY_PASSWORD);
    String validUserName = generateUsernameFromEmail(profile.getEmail());
    user.setUsername(validUserName);
    user.setRole(Constants.USER_ROLE);
    user.setSignupSource(SignupSource.GOOGLE);
    return user;
  }

  String generateUsernameFromEmail(String email) {
    String prefix = email.substring(0, email.indexOf("@"));
    prefix = prefix + RandomStringUtils.randomAlphabetic(5);
    return prefix.replaceAll(User.DISALLOWED_USERNAME_CHARS_REGEXP, "-");
  }
}
