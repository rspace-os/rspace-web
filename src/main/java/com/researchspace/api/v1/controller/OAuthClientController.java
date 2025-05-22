package com.researchspace.api.v1.controller;

import static com.researchspace.auth.OAuthScopes.SCOPE_ALL;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.IgnoreInLoggingInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for OAuth client apps making requests, where they authenticate with their clientId and
 * secret
 */
@ApiController
@RequestMapping("/oauth")
public class OAuthClientController {

  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  @Autowired private UserManager userManager;

  @Autowired private OAuthTokenManager tokenManager;

  @Autowired private IReauthenticator reauthenticator;

  @Autowired private ApiAvailabilityHandler apiHandler;

  @Autowired private AnalyticsManager analyticsMgr;

  private @Autowired SystemPropertyPermissionManager systemPropertyMgr;

  /** Main endpoint for token grants. New or refreshed. */
  @PostMapping("/token")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"client_secret", "password"})
  public NewOAuthTokenResponse getToken(
      @RequestParam(name = "grant_type") String grantType,
      @RequestParam(name = "username", required = false) String username,
      @RequestParam(required = false) String password,
      @RequestParam(name = "refresh_token", required = false) String refreshToken,
      @RequestParam(name = "client_secret") String clientSecret,
      @RequestParam(name = "client_id") String clientId,
      @RequestParam(name = "is_jwt", defaultValue = "false", required = false) Boolean isJwt,
      HttpServletRequest request) {

    if (StringUtils.isEmpty(clientId)) {
      throw new ApiAuthenticationException("Parameter client_id must be present!");
    }
    if (StringUtils.isEmpty(clientSecret)) {
      throw new ApiAuthenticationException("Parameter client_secret must be present!");
    }
    if (!apiHandler.isApiAvailableForUser(null)) {
      throw new ApiAuthenticationException(
          "Access to API has been disabled by RSpace administrator.");
    }

    boolean oauthAuthenticationEnabled =
        systemPropertyMgr.isPropertyAllowed(
            (User) null, SystemPropertyName.API_OAUTH_AUTHENTICATION);
    if (!oauthAuthenticationEnabled) {
      throw new ApiAuthenticationException(
          "OAuth authentication has been disabled by RSpace administrator.");
    }

    NewOAuthTokenResponse response;
    if ("password".equals(grantType)) {
      if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
        throw new IllegalArgumentException(
            "Password grant requires parameters `username` and `password` to be present.");
      }
      try {
        User user = userManager.getUserByUsernameOrAlias(username);
        if (!username.equals(user.getUsername())) {
          SECURITY_LOG.info(
              String.format(
                  "Processing login of user [%s] through username alias [%s]",
                  user.getUsername(), username));
        }

        if (!apiHandler.isApiAvailableForUser(user)) {
          throw new ApiAuthenticationException(
              "User '" + user.getUsername() + "' doesn't have access to API");
        }
        if (user.isLoginDisabled()) {
          throw new ApiAuthenticationException(
              "User '" + user.getUsername() + "' has their account locked or disabled.");
        }

        response = passwordGrant(clientId, clientSecret, user, password, isJwt);
        analyticsMgr.apiAccessed(user, false, request);

      } catch (DataAccessException e) {
        SECURITY_LOG.warn("OAuth password flow request for unknown user: " + username);
        throw new ApiAuthenticationException("Invalid user credentials.");
      }
      return response;
    }

    if ("refresh_token".equals(grantType)) {
      if (StringUtils.isEmpty(refreshToken)) {
        throw new IllegalArgumentException(
            "Refresh grant requires parameter `refresh_token` to be present.");
      }

      ServiceOperationResult<Void> validationResult = tokenManager.validateToken(refreshToken);
      if (!validationResult.isSucceeded()) {
        throw new IllegalArgumentException(validationResult.getMessage());
      }
      return refreshGrant(clientId, clientSecret, refreshToken, isJwt);
    }

    throw new IllegalArgumentException(
        "Only password grant and token refresh is supported for OAuth at this time.");
  }

  private NewOAuthTokenResponse refreshGrant(
      String clientId, String clientSecret, String refreshToken, Boolean isJwt) {
    ServiceOperationResult<NewOAuthTokenResponse> response;
    if (isJwt) {
      response = tokenManager.refreshJwtAccessToken(clientId, clientSecret, refreshToken);
    } else {
      response = tokenManager.refreshAccessToken(clientId, clientSecret, refreshToken);
    }
    if (!response.isSucceeded()) {
      SECURITY_LOG.warn("refresh_token flow request with invalid refreshToken");
      throw new NotFoundException(response.getMessage());
    }
    return response.getEntity();
  }

  private NewOAuthTokenResponse passwordGrant(
      String clientId, String clientSecret, User subject, String password, Boolean isJwt) {

    boolean credentialsMatch = reauthenticator.reauthenticate(subject, password);

    if (!credentialsMatch) {
      SECURITY_LOG.warn(
          "OAuth password flow request with invalid credentials for user: "
              + subject.getUsername());
      throw new ApiAuthenticationException("Invalid user credentials.");
    }
    ServiceOperationResult<NewOAuthTokenResponse> response;
    if (isJwt) {
      response = tokenManager.createNewJwtToken(clientId, clientSecret, subject, SCOPE_ALL);
    } else {
      response = tokenManager.createNewToken(clientId, clientSecret, subject, SCOPE_ALL);
    }
    if (!response.isSucceeded()) {
      throw new ApiAuthenticationException(response.getMessage());
    }
    return response.getEntity();
  }
}
