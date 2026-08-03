package com.researchspace.api.v2.auth;

import static com.researchspace.model.UserApiKey.APIKEY_REGEX;
import static org.apache.commons.lang3.StringUtils.abbreviate;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.auth.ApiKeyAuthenticationToken;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.service.UserManager;
import com.researchspace.session.SessionAttributeUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authenticates REST API v2 requests using an API key, OAuth bearer token, or existing browser
 * session.
 *
 * <p>Deviates from REST API v1 in several ways; see the "Deviations from REST API v1" table in
 * {@code DevDocs/DeveloperNotes/RestApiV2Collections.md}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApiV2Authenticator {

  private static final Logger API_REQUEST_LOG =
      LoggerFactory.getLogger("com.researchspace.api.v2.requests");

  /**
   * Request attribute set when authentication reused the caller's existing Shiro session rather
   * than creating one. {@link com.researchspace.api.v2.controller.ApiV2AuthenticationInterceptor}
   * uses it to avoid logging out a session it did not create.
   */
  public static final String SESSION_REUSED_ATTRIBUTE = "rspace.api.v2.sessionReused";

  private final UserApiKeyManager apiKeyManager;
  private final OAuthTokenManager oAuthTokenManager;
  private final AnalyticsManager analyticsManager;
  private final ApiAvailabilityHandler apiAvailabilityHandler;
  private final UserManager userManager;

  public User authenticate(HttpServletRequest request) {
    return authenticateIfPresent(request)
        .orElseThrow(
            () ->
                new ApiV2AuthenticationException(
                    "API authentication information is missing - please include your apiKey as a"
                        + " header in the format 'apiKey:myAPikey' or with OAuth in the format"
                        + " 'Authorization: Bearer <myAccessToken>'."));
  }

  /**
   * Authenticates supplied API credentials or reuses an existing browser session.
   *
   * <p>An empty result means the request is genuinely anonymous. Invalid credentials still throw
   * rather than silently degrading to anonymous access.
   */
  public Optional<User> authenticateIfPresent(HttpServletRequest request) {
    String apiKey = request.getHeader("apiKey");
    if (apiKey != null && !apiKey.isEmpty()) {
      return Optional.of(authenticateApiKey(request, apiKey));
    }
    String authorization = request.getHeader("Authorization");
    if (authorization != null && !authorization.isEmpty()) {
      return Optional.of(authenticateOAuth(request, authorization));
    }
    return existingSessionUser(request);
  }

  public void logout() {
    SecurityUtils.getSubject().logout();
  }

  private User authenticateApiKey(HttpServletRequest request, String apiKey) {
    if (!apiAvailabilityHandler.isApiAvailableForUser(null)) {
      throw new ApiV2AuthenticationException(
          "Access to API has been disabled by RSpace administrator.");
    }
    if (!apiKey.matches(APIKEY_REGEX)) {
      throw new ApiV2AuthenticationException("API key invalid - must match regexp:" + APIKEY_REGEX);
    }
    User user = apiKeyManager.findUserByKey(apiKey).orElseThrow(() -> unknownToken(apiKey));
    if (!apiAvailabilityHandler.isApiAvailableForUser(user)) {
      throw new ApiV2AuthenticationException(
          String.format("Access to API has been disabled for user '%s'", user.getUsername()));
    }
    // Deviation from v1: set the authentication method BEFORE authenticateToken, so that
    // isExternalApiCall() sees it and refuses to reuse a browser session for an API-key call. v1
    // sets it afterwards, which leaves its own session-reuse guard permanently dead.
    user.setAuthenticatedBy(UserAuthenticationMethod.API_KEY);
    user = authenticateToken(request, apiKey, user);
    logExternalApiRequest(request, user);
    return user;
  }

  private User authenticateOAuth(HttpServletRequest request, String authorization) {
    String[] headerParts = authorization.split("\\s+");
    if (headerParts.length != 2 || !"Bearer".equals(headerParts[0])) {
      throw new ApiV2AuthenticationException(
          "Authorization header for OAuth must be in the form \"Bearer <myAccessToken>\"");
    }
    String tokenValue = headerParts[1];
    ServiceOperationResult<Void> validation = oAuthTokenManager.validateToken(tokenValue);
    if (!validation.isSucceeded()) {
      throw new ApiV2AuthenticationException(validation.getMessage());
    }
    ServiceOperationResult<OAuthToken> authentication = oAuthTokenManager.authenticate(tokenValue);
    if (!authentication.isSucceeded()) {
      throw unknownToken(tokenValue);
    }
    OAuthToken token = authentication.getEntity();
    User user = token.getUser();
    user.setAuthenticatedBy(
        OAuthTokenType.UI_TOKEN.equals(token.getTokenType())
            ? UserAuthenticationMethod.UI_OAUTH_TOKEN
            : UserAuthenticationMethod.API_OAUTH_TOKEN);
    // Deviation from v1: external OAuth callers are also subject to the deployment-wide and
    // per-user "API disabled by administrator" settings, which v1 only enforces on the API-key
    // path. Scoped to API_OAUTH_TOKEN so that UI tokens keep working when the public API is off.
    if (UserAuthenticationMethod.API_OAUTH_TOKEN.equals(user.getAuthenticatedBy())) {
      if (!apiAvailabilityHandler.isApiAvailableForUser(null)) {
        throw new ApiV2AuthenticationException(
            "Access to API has been disabled by RSpace administrator.");
      }
      if (!apiAvailabilityHandler.isApiAvailableForUser(user)) {
        throw new ApiV2AuthenticationException(
            String.format("Access to API has been disabled for user '%s'", user.getUsername()));
      }
      if (!apiAvailabilityHandler.isOAuthAccessAllowed(user)) {
        throw new ApiV2AuthenticationException(
            String.format(
                "Access through OAuth tokens has been disabled for user '%s'", user.getUsername()));
      }
    }
    user = authenticateToken(request, tokenValue, user);
    if (UserAuthenticationMethod.API_OAUTH_TOKEN.equals(user.getAuthenticatedBy())) {
      logExternalApiRequest(request, user);
    }
    return user;
  }

  private User authenticateToken(HttpServletRequest request, String token, User user) {
    if (user.isLoginDisabled()) {
      throw new ApiV2AuthenticationException(
          String.format(
              "Api access denied as account for user '%s', who is associated with provided "
                  + "authentication token, is locked or disabled",
              user.getUsername()));
    }
    try {
      Subject shiroSubject = SecurityUtils.getSubject();
      if (shiroSubject.getPrincipal() != null && !isExternalApiCall(user)) {
        Session session = shiroSubject.getSession();
        User subject = (User) session.getAttribute(SessionAttributeUtils.USER);
        Boolean isRunAs = (Boolean) session.getAttribute(SessionAttributeUtils.IS_RUN_AS);
        if (subject != null
            && (Boolean.TRUE.equals(isRunAs) || subject.getUsername().equals(user.getUsername()))) {
          log.info(
              "Reusing the current session for API authentication, principal={}",
              user.getUsername());
          request.setAttribute(SESSION_REUSED_ATTRIBUTE, Boolean.TRUE);
          return subject;
        }
      }
    } catch (UnavailableSecurityManagerException ignored) {
      // Session reuse is unavailable in tests without a Shiro security manager.
    }
    try {
      SecurityUtils.getSubject().login(new ApiKeyAuthenticationToken(user.getUsername(), token));
    } catch (AuthenticationException ex) {
      // Deviation from v1: v1 lets Shiro's AuthenticationException escape uncaught, so a realm
      // failure surfaces as a generic 500. Translate it to the 401 it actually is. Deliberately
      // narrow: UnavailableSecurityManagerException must stay a 500, as it means misconfiguration.
      log.warn("Shiro rejected REST API v2 credentials for user {}", user.getUsername(), ex);
      throw new ApiV2AuthenticationException(
          String.format("User could not be authenticated for token %s...", abbreviate(token, 4)));
    }
    return user;
  }

  private Optional<User> existingSessionUser(HttpServletRequest request) {
    try {
      Subject subject = SecurityUtils.getSubject();
      Object principal = subject.getPrincipal();
      if (!(principal instanceof String username)) {
        return Optional.empty();
      }
      Session session = subject.getSession(false);
      if (session == null) {
        return Optional.empty();
      }
      Object cachedUser = session.getAttribute(SessionAttributeUtils.USER);
      if (cachedUser instanceof User authenticatedUser
          && username.equals(authenticatedUser.getUsername())) {
        return reuseSessionUser(request, authenticatedUser);
      }

      // A run-as operation changes Shiro's active principal but deliberately leaves the session's
      // cached USER as the original administrator. Resolve the active principal without replacing
      // that cache, otherwise REST requests made while operating as another user regain the
      // administrator identity (and releasing run-as would leave the cache pointing at the target).
      return reuseSessionUser(request, userManager.getUserByUsernameNoSession(username));
    } catch (UnavailableSecurityManagerException ignored) {
      return Optional.empty();
    }
  }

  private static Optional<User> reuseSessionUser(HttpServletRequest request, User user) {
    if (user == null || user.isAnonymousGuestAccount()) {
      return Optional.empty();
    }
    request.setAttribute(SESSION_REUSED_ATTRIBUTE, Boolean.TRUE);
    return Optional.of(user);
  }

  private static boolean isExternalApiCall(User user) {
    return UserAuthenticationMethod.API_OAUTH_TOKEN.equals(user.getAuthenticatedBy())
        || UserAuthenticationMethod.API_KEY.equals(user.getAuthenticatedBy());
  }

  private static ApiV2AuthenticationException unknownToken(String token) {
    return new ApiV2AuthenticationException(
        String.format("User could not be authenticated for token %s...", abbreviate(token, 4)));
  }

  private void logExternalApiRequest(HttpServletRequest request, User user) {
    API_REQUEST_LOG.info(
        "{} [{}] from {}, for user [{}] ({})",
        request.getMethod(),
        request.getRequestURI(),
        RequestUtil.remoteAddr(request),
        user.getUsername(),
        user.getAuthenticatedBy());
    analyticsManager.publicApiUsed(user, request);
  }
}
