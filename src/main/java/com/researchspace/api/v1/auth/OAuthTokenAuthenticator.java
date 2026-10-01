package com.researchspace.api.v1.auth;

import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.OAuthTokenManager;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Authenticates API requests by using the supplied OAuth token that RSpace issues. */
@Service
public class OAuthTokenAuthenticator extends AbstractApiAuthenticator {

  @Autowired private OAuthTokenManager tokenManager;

  Function<String, Optional<AuthenticationResult>> findUserForToken() {
    return accessToken -> {
      // Session-bound UI tokens are credentials for API v2 only. API v1 has no browser-session
      // context with which to verify their subject and delegation claims, so accepting one here
      // would reduce it to an ordinary bearer token.
      if (tokenManager.getUiTokenContext(accessToken).isPresent()) {
        return Optional.empty();
      }
      ServiceOperationResult<OAuthToken> subjectRetrieval = tokenManager.authenticate(accessToken);
      if (!subjectRetrieval.isSucceeded()) {
        return Optional.empty();
      }
      OAuthToken token = subjectRetrieval.getEntity();
      UserAuthenticationMethod method =
          OAuthTokenType.UI_TOKEN.equals(token.getTokenType())
              ? UserAuthenticationMethod.UI_OAUTH_TOKEN
              : UserAuthenticationMethod.API_OAUTH_TOKEN;
      return Optional.ofNullable(token.getUser())
          .map(user -> new AuthenticationResult(user, method));
    };
  }

  /**
   * @return OAuth access token
   */
  String retrieveTokenFromHeader(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (StringUtils.isBlank(header)) {
      throw new ApiAuthenticationException("api.errors.authentication.oauthHeaderInvalid");
    }
    String[] headerParts = header.split("\\s+");
    if (headerParts.length != 2 || !(headerParts[0].equals("Bearer"))) {
      throw new ApiAuthenticationException("api.errors.authentication.oauthHeaderInvalid");
    }
    ServiceOperationResult<Void> result = tokenManager.validateToken(headerParts[1]);
    if (!result.isSucceeded()) {
      throw new ApiAuthenticationException("api.errors.authentication.oauthTokenInvalid");
    }
    return headerParts[1];
  }
}
