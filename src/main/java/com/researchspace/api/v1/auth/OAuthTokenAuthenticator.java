package com.researchspace.api.v1.auth;

import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.OAuthTokenManager;
import java.util.Optional;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
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
        OAuthToken token = subjectRetrieval.getEntity();
        boolean isUiToken = OAuthTokenType.UI_TOKEN.equals(token.getTokenType());
        user = token.getUser();
        user.setAuthenticatedBy(
            isUiToken
                ? UserAuthenticationMethod.UI_OAUTH_TOKEN
                : UserAuthenticationMethod.API_OAUTH_TOKEN);
      }
      return Optional.ofNullable(user);
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
