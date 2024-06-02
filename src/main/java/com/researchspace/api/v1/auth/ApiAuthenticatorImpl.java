package com.researchspace.api.v1.auth;

import com.researchspace.model.User;
import com.researchspace.service.UserApiKeyManager;
import javax.servlet.http.HttpServletRequest;

/**
 * Main authenticator implementation, that checks if it should call the ApiKeyAuthenticator or the
 * OAuthTokenAuthenticator.
 */
public class ApiAuthenticatorImpl implements ApiAuthenticator {

  private ApiKeyAuthenticator apiKeyAuthenticator;
  private OAuthTokenAuthenticator oAuthAuthenticator;

  public ApiAuthenticatorImpl(
      UserApiKeyManager userApiKeyManager, OAuthTokenAuthenticator oAuthAuthenticator) {
    apiKeyAuthenticator = new ApiKeyAuthenticator(userApiKeyManager);
    this.oAuthAuthenticator = oAuthAuthenticator;
  }

  @Override
  public User authenticate(HttpServletRequest request) {
    if (request.getHeader("apiKey") != null) {
      return apiKeyAuthenticator.authenticate(request);
    } else if (request.getHeader("Authorization") != null) {
      return oAuthAuthenticator.authenticate(request);
    } else {
      throw new ApiAuthenticationException(
          "API key is missing - please include your apiKey as a header in the format"
              + " 'apiKey:myAPikey' or with OAuth in the format 'Authorization: Bearer"
              + " <myAccessToken>'.");
    }
  }
}
