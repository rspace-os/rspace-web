package com.researchspace.api.v1.auth;

import static com.researchspace.model.UserApiKey.APIKEY_REGEX;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.researchspace.model.User;
import com.researchspace.service.UserApiKeyManager;
import java.util.Optional;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.Validate;

/** Validates and authenticates API requests accessed using an api key. */
public class ApiKeyAuthenticator extends AbstractApiAuthenticator {

  private UserApiKeyManager apiMgr;

  public ApiKeyAuthenticator(UserApiKeyManager apiMgr) {
    Validate.notNull(apiMgr, "API key manager cannot be null");
    this.apiMgr = apiMgr;
  }

  String retrieveTokenFromHeader(HttpServletRequest request) {
    String apiKey = request.getHeader("apiKey");
    if (isEmpty(apiKey)) {
      throw new ApiAuthenticationException("api.errors.authentication.apiKeyMissing");
    } else if (!apiKey.matches(APIKEY_REGEX)) {
      throw new ApiAuthenticationException("api.errors.authentication.apiKeyInvalid", APIKEY_REGEX);
    }

    return apiKey;
  }

  Function<String, Optional<User>> findUserForToken() {
    return apiKey -> apiMgr.findUserByKey(apiKey);
  }
}
