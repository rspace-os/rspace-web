package com.researchspace.api.v2.auth;

import static com.researchspace.model.UserApiKey.APIKEY_REGEX;
import static org.apache.commons.lang3.StringUtils.abbreviate;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.UserApiKeyManager;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authenticates REST API v2 requests using an API key or OAuth bearer token.
 *
 * <p>Deviates from REST API v1 in several ways; see the "Deviations from REST API v1" table in
 * {@code DevDocs/DeveloperNotes/RestApiV2Collections.md}.
 */
@Service
@RequiredArgsConstructor
public class ApiV2Authenticator {

  private static final Logger API_REQUEST_LOG =
      LoggerFactory.getLogger("com.researchspace.api.v2.requests");

  private final UserApiKeyManager apiKeyManager;
  private final OAuthTokenManager oAuthTokenManager;
  private final AnalyticsManager analyticsManager;
  private final ApiAvailabilityHandler apiAvailabilityHandler;

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
   * Authenticates supplied API credentials.
   *
   * <p>An empty result means no API credentials were supplied. Browser session cookies are
   * deliberately ignored, so they cannot elevate an otherwise anonymous REST request. Invalid API
   * credentials still throw rather than silently degrading to anonymous access.
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
    return Optional.empty();
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
    user.setAuthenticatedBy(UserAuthenticationMethod.API_KEY);
    assertLoginAllowed(user);
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
    assertLoginAllowed(user);
    if (UserAuthenticationMethod.API_OAUTH_TOKEN.equals(user.getAuthenticatedBy())) {
      logExternalApiRequest(request, user);
    }
    return user;
  }

  private static void assertLoginAllowed(User user) {
    if (user.isLoginDisabled()) {
      throw new ApiV2AuthenticationException(
          String.format(
              "Api access denied as account for user '%s', who is associated with provided "
                  + "authentication token, is locked or disabled",
              user.getUsername()));
    }
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
