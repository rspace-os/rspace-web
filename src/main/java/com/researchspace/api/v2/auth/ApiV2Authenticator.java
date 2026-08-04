package com.researchspace.api.v2.auth;

import static com.researchspace.model.UserApiKey.APIKEY_REGEX;

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
    return authenticateIfPresent(request).orElseThrow(ApiV2AuthenticationException::new);
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
      throw new ApiV2AuthenticationException();
    }
    if (!apiKey.matches(APIKEY_REGEX)) {
      throw new ApiV2AuthenticationException();
    }
    User user = apiKeyManager.findUserByKey(apiKey).orElseThrow(ApiV2Authenticator::unknownToken);
    if (!apiAvailabilityHandler.isApiAvailableForUser(user)) {
      throw new ApiV2AuthenticationException();
    }
    user.setAuthenticatedBy(UserAuthenticationMethod.API_KEY);
    assertLoginAllowed(user);
    logExternalApiRequest(request, user);
    return user;
  }

  private User authenticateOAuth(HttpServletRequest request, String authorization) {
    String[] headerParts = authorization.split("\\s+");
    if (headerParts.length != 2 || !"Bearer".equals(headerParts[0])) {
      throw new ApiV2AuthenticationException();
    }
    String tokenValue = headerParts[1];
    ServiceOperationResult<Void> validation = oAuthTokenManager.validateToken(tokenValue);
    if (!validation.isSucceeded()) {
      throw new ApiV2AuthenticationException();
    }
    ServiceOperationResult<OAuthToken> authentication = oAuthTokenManager.authenticate(tokenValue);
    if (!authentication.isSucceeded()) {
      throw unknownToken();
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
        throw new ApiV2AuthenticationException();
      }
      if (!apiAvailabilityHandler.isApiAvailableForUser(user)) {
        throw new ApiV2AuthenticationException();
      }
      if (!apiAvailabilityHandler.isOAuthAccessAllowed(user)) {
        throw new ApiV2AuthenticationException();
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
      throw new ApiV2AuthenticationException();
    }
  }

  private static ApiV2AuthenticationException unknownToken() {
    return new ApiV2AuthenticationException();
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
