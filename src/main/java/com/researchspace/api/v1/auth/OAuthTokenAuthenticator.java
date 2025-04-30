package com.researchspace.api.v1.auth;

import com.researchspace.model.User;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.OAuthTokenManager;
import java.util.Optional;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Authenticates API requests by using the supplied OAuth token that RSpace issues. */
@Service
public class OAuthTokenAuthenticator extends AbstractApiAuthenticator {

  @Autowired private OAuthTokenManager tokenManager;

  Function<String, Optional<User>> findUserForToken() {
    return accessToken -> {
      ServiceOperationResult<OAuthToken> subjectRetrieval = tokenManager.authenticate(accessToken);
      User user = null;
      if (subjectRetrieval.isSucceeded()) {
        user = subjectRetrieval.getEntity().getUser();
      }
      return Optional.ofNullable(user);
    };
  }

  /**
   * @return OAuth access token
   */
  String retrieveTokenFromHeader(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    String[] headerParts = header.split("\\s+");
    if (headerParts.length != 2 || !(headerParts[0].equals("Bearer"))) {
      throw new ApiAuthenticationException(
          "Authorization header for OAuth must be in the form \"Bearer <myAccessToken>\"");
    }
    ServiceOperationResult<Void> result = tokenManager.validateToken(headerParts[1]);
    if (!result.isSucceeded()) {
      throw new ApiAuthenticationException(result.getMessage());
    }
    return headerParts[1];
  }
}
