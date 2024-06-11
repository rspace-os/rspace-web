package com.researchspace.api.v1.controller;

import static com.researchspace.auth.OAuthScopes.SCOPE_ALL;

import com.researchspace.api.v1.auth.ApiAuthenticationException;
import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.IgnoreInLoggingInterceptor;
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

  /** Main endpoint for token grants. New or refreshed. */
  @PostMapping("/token")
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"client_secret", "password"})
  public NewOAuthTokenResponse getToken(
      @RequestParam(name = "grant_type") String grantType,
      @RequestParam(name = "username", required = false) String userName,
      @RequestParam(required = false) String password,
      @RequestParam(name = "refresh_token", required = false) String refreshToken,
      @RequestParam(name = "client_secret") String clientSecret,
      @RequestParam(name = "client_id") String clientId,
      @RequestParam(name = "is_jwt", defaultValue = "false", required = false) Boolean isJwt) {

    if (StringUtils.isEmpty(clientId)) {
      throw new ApiAuthenticationException("Parameter client_id must be present!");
    }
    if (StringUtils.isEmpty(clientSecret)) {
      throw new ApiAuthenticationException("Parameter client_secret must be present!");
    }

    switch (grantType) {
      case "password":
        if (StringUtils.isEmpty(userName) || StringUtils.isEmpty(password)) {
          throw new IllegalArgumentException(
              "Password grant requires parameters `username` and `password` to be present.");
        }
        try {
          User user = userManager.getUserByUsernameOrAlias(userName);

          if (user.isLoginDisabled()) {
            throw new ApiAuthenticationException(
                "User '" + userName + "' has their account locked or disabled.");
          }

          return passwordGrant(clientId, clientSecret, user, password, isJwt);
        } catch (DataAccessException e) {
          SECURITY_LOG.warn("OAuth password flow request for unknown user: " + userName);
          throw new ApiAuthenticationException("Invalid user credentials.");
        }
      case "refresh_token":
        if (StringUtils.isEmpty(refreshToken)) {
          throw new IllegalArgumentException(
              "Refresh grant requires parameter `refresh_token` to be present.");
        }

        ServiceOperationResult<Void> validationResult = tokenManager.validateToken(refreshToken);

        if (!validationResult.isSucceeded()) {
          throw new IllegalArgumentException(validationResult.getMessage());
        }

        return refreshGrant(clientId, clientSecret, refreshToken, isJwt);
      default:
        throw new IllegalArgumentException(
            "Only password grant and token refresh is supported for OAuth at this time.");
    }
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
