package com.researchspace.api.v1.auth;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.UserApiKeyManager;
import javax.servlet.http.HttpServletRequest;

/**
 * Main authenticator implementation, that checks if it should call the ApiKeyAuthenticator or the
 * OAuthTokenAuthenticator.
 */
public class MainRSpaceApiAuthenticator implements ApiAuthenticator {

  private ApiKeyAuthenticator apiKeyAuthenticator;
  private OAuthTokenAuthenticator oAuthAuthenticator;
  private AnalyticsManager analyticsMgr;
  private ApiAvailabilityHandler apiHandler;

  public MainRSpaceApiAuthenticator(
      UserApiKeyManager userApiKeyManager,
      OAuthTokenAuthenticator oAuthAuthenticator,
      AnalyticsManager analyticsManager,
      ApiAvailabilityHandler apiAvailabilityHandler) {

    this.apiKeyAuthenticator = new ApiKeyAuthenticator(userApiKeyManager);
    this.oAuthAuthenticator = oAuthAuthenticator;
    this.analyticsMgr = analyticsManager;
    this.apiHandler = apiAvailabilityHandler;
  }

  @Override
  public User authenticate(HttpServletRequest request) {
    if (request.getHeader("apiKey") != null) {
      if (!apiHandler.isApiAvailableForUser(null)) {
        throw new ApiAuthenticationException(
            "Access to API has been disabled by RSpace administrator.");
      }
      User user = apiKeyAuthenticator.authenticate(request);
      if (user != null) {
        if (!apiHandler.isApiAvailableForUser(user)) {
          throw new ApiAuthenticationException(
              String.format("Access to API has been disabled for user '%s'", user.getUsername()));
        }
        analyticsMgr.apiUsed(user, request);
      }
      return user;
    }

    if (request.getHeader("Authorization") != null) {
      return oAuthAuthenticator.authenticate(request);
    }

    throw new ApiAuthenticationException(
        "API key is missing - please include your apiKey as a header in the format"
            + " 'apiKey:myAPikey' or with OAuth in the format 'Authorization: Bearer"
            + " <myAccessToken>'.");
  }
}
