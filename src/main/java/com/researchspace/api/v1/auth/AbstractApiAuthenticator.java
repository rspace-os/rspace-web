package com.researchspace.api.v1.auth;

import static com.researchspace.core.util.StringAbbreviationUtils.abbreviate;

import com.researchspace.auth.ApiKeyAuthenticationToken;
import com.researchspace.auth.StatelessApiLogin;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.session.SessionAttributeUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.lang.ShiroException;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;

/*
 * Base class with template /strategy patterns for overriding
 */
@Slf4j
abstract class AbstractApiAuthenticator implements ApiAuthenticator {
  record AuthenticationResult(User user, UserAuthenticationMethod method) {}

  /**
   * Retrieve token from HttpRequest, validating syntax as well
   *
   * @param request
   * @return
   */
  abstract String retrieveTokenFromHeader(HttpServletRequest request);

  /** Retrieve the user and authentication method for the given token. */
  abstract Function<String, Optional<AuthenticationResult>> findUserForToken();

  @Override
  public User authenticate(HttpServletRequest request) {
    String accessToken = retrieveTokenFromHeader(request);
    Optional<AuthenticationResult> result = findUserForToken().apply(accessToken);

    if (!result.isPresent()) {
      throw new ApiAuthenticationException(
          "api.errors.authentication.tokenUnknown", abbreviate(accessToken, 4));
    }

    User targetUser = result.get().user();
    targetUser.setAuthenticatedBy(result.get().method());
    assertLoginAllowed(targetUser);

    if (UserAuthenticationMethod.UI_OAUTH_TOKEN.equals(targetUser.getAuthenticatedBy())) {
      return authenticateUiToken(targetUser);
    }

    // this login must not disturb any session that arrived with the request (for example a
    // browser session cookie sent alongside the API key); see StatelessApiLogin
    doLogin(accessToken, targetUser);
    return targetUser;
  }

  private User authenticateUiToken(User targetUser) {
    try {
      Subject shiroSubject = SecurityUtils.getSubject();
      if (shiroSubject.getPrincipal() == null) {
        throw invalidUiToken();
      }

      Session session = shiroSubject.getSession(false);
      if (session == null) {
        throw invalidUiToken();
      }

      User sessionUser = (User) session.getAttribute(SessionAttributeUtils.USER);
      if (sessionUser == null || !sessionUser.getUsername().equals(targetUser.getUsername())) {
        throw invalidUiToken();
      }

      log.info(
          "Reusing the current session for API authentication, principal={}",
          targetUser.getUsername());
      return targetUser;
    } catch (ShiroException e) {
      log.warn("Unable to reuse browser session for UI OAuth token authentication", e);
      throw invalidUiToken();
    }
  }

  private ApiAuthenticationException invalidUiToken() {
    return new ApiAuthenticationException("api.errors.authentication.oauthTokenInvalid");
  }

  /*
   * Package scoped for testing
   */
  void doLogin(String apiKey, User u) {
    StatelessApiLogin.login(
        SecurityUtils.getSubject(), new ApiKeyAuthenticationToken(u.getUsername(), apiKey));
  }

  @Override
  public void logout() {
    SecurityUtils.getSubject().logout();
  }

  private void assertLoginAllowed(User user) {
    if (user.isLoginDisabled()) {
      throw new ApiAuthenticationException(
          "api.errors.authentication.accountDisabled", user.getUsername());
    }
  }
}
