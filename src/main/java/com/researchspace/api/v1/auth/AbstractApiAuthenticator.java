package com.researchspace.api.v1.auth;

import static org.apache.commons.lang.StringUtils.abbreviate;

import com.researchspace.auth.ApiKeyAuthenticationToken;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IUserPermissionUtils;
import com.researchspace.session.SessionAttributeUtils;
import java.util.Optional;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;

/*
 * Base class with template /strategy patterns for overriding
 */
@Slf4j
abstract class AbstractApiAuthenticator implements ApiAuthenticator {
  private @Autowired IUserPermissionUtils userPermissionUtils;

  /*
   * Package scoped for testing
   */
  void doLogin(String apiKey, User u) {
    SecurityUtils.getSubject().login(new ApiKeyAuthenticationToken(u.getUsername(), apiKey));
  }

  /**
   * Retrieve token from HttpRequest, validating syntax as well
   *
   * @param request
   * @return
   */
  abstract String retrieveTokenFromHeader(HttpServletRequest request);

  /** Retrieve user for given String token. */
  abstract Function<String, Optional<User>> findUserForToken();

  @Override
  public User authenticate(HttpServletRequest request) {
    String accessToken = retrieveTokenFromHeader(request);
    Optional<User> userOpt = findUserForToken().apply(accessToken);

    if (!userOpt.isPresent()) {
      throw new ApiAuthenticationException(
          String.format(
              "User could not be authenticated for token %s", abbreviate(accessToken, 10)));
    }

    User targetUser = userOpt.get();
    assertLoginAllowed(targetUser);

    // Requests for inventory from the browser come with JSESSIONID cookies. Hence
    // we have access to the current session. We reuse the session when possible.
    // Session reuse happens to solve RSINV-254.
    try {
      Subject shiroSubject = SecurityUtils.getSubject();

      // In some tests shiroSubject exists, but principal is null
      if (shiroSubject.getPrincipal() != null) {
        Session session = shiroSubject.getSession();
        User subject = (User) session.getAttribute(SessionAttributeUtils.USER);
        Boolean isRunAs = (Boolean) session.getAttribute(SessionAttributeUtils.IS_RUN_AS);

        // Some tests break without this check
        boolean isContextNonNull = subject != null && userPermissionUtils != null;

        boolean shouldReuseSession =
            isContextNonNull
                && ((isRunAs != null && isRunAs)
                    || subject.getUsername().equals(targetUser.getUsername()));

        if (shouldReuseSession) {
          log.info(
              "Reusing the current session for API authentication, principal={}",
              targetUser.getUsername());
          return subject;
        }
      }
    } catch (UnavailableSecurityManagerException ignored) {
      // This exception only happens in some tests.
      // When it does, we know that session  preservation is not possible.
    }

    doLogin(accessToken, targetUser);
    return targetUser;
  }

  public void logout() {
    SecurityUtils.getSubject().logout();
  }

  private void assertLoginAllowed(User user) {
    if (user.isLoginDisabled()) {
      throw new ApiAuthenticationException(
          String.format(
              "Api access denied as account for user '%s' associated with provided API key is "
                  + "locked or disabled",
              user.getUsername()));
    }
  }
}
