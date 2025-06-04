package com.researchspace.api.v1.auth;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.api.v1.service.impl.ApiRequestLogger;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.service.ApiAvailabilityHandler;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main authenticator implementation in RSpace, supports API authentication through
 * ApiKeyAuthenticator and OAuthTokenAuthenticator, depending on passed request headers.
 */
public class CombinedApiAuthenticator implements ApiAuthenticator {

  protected static final Logger API_REQUEST_LOG = LoggerFactory.getLogger(ApiRequestLogger.class);

  private ApiKeyAuthenticator apiKeyAuthenticator;
  private OAuthTokenAuthenticator oAuthAuthenticator;
  private AnalyticsManager analyticsMgr;
  private ApiAvailabilityHandler apiHandler;

  public CombinedApiAuthenticator(
      ApiKeyAuthenticator apiKeyAuthenticator,
      OAuthTokenAuthenticator oAuthAuthenticator,
      AnalyticsManager analyticsManager,
      ApiAvailabilityHandler apiAvailabilityHandler) {

    this.apiKeyAuthenticator = apiKeyAuthenticator;
    this.oAuthAuthenticator = oAuthAuthenticator;
    this.analyticsMgr = analyticsManager;
    this.apiHandler = apiAvailabilityHandler;
  }

  @Override
  public User authenticate(HttpServletRequest request) {
    if (StringUtils.isNotEmpty(request.getHeader("apiKey"))) {
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
        user.setAuthenticatedBy(UserAuthenticationMethod.API_KEY);
        logExternalApiRequest(request, user);
      }
      return user;
    }

    if (StringUtils.isNotEmpty(request.getHeader("Authorization"))) {
      User user = oAuthAuthenticator.authenticate(request);
      if (UserAuthenticationMethod.API_OAUTH_TOKEN.equals(user.getAuthenticatedBy())) {
        logExternalApiRequest(request, user);
      }
      return user;
    }

    throw new ApiAuthenticationException(
        "API authentication information is missing - please include your apiKey as a header in the"
            + " format 'apiKey:myAPikey' or with OAuth in the format 'Authorization: Bearer"
            + " <myAccessToken>'.");
  }

  private void logExternalApiRequest(HttpServletRequest request, User user) {
    API_REQUEST_LOG.info(
        "{} [{}] from {}, for user [{}] ({})",
        request.getMethod(),
        request.getRequestURI(),
        RequestUtil.remoteAddr(request),
        user.getUsername(),
        user.getAuthenticatedBy());
    analyticsMgr.publicApiUsed(user, request);
  }
}
