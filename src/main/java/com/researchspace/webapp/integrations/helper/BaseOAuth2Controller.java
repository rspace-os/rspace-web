package com.researchspace.webapp.integrations.helper;

import static com.researchspace.session.SessionAttributeUtils.getSessionAttribute;
import static com.researchspace.session.SessionAttributeUtils.removeSessionAttribute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.webapp.controller.BaseController;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Extends BaseController to provide some utility methods for controllers implementing OAuth2
 * workflow.
 */
public class BaseOAuth2Controller extends BaseController {

  protected @Autowired UserConnectionManager userConnectionManager;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class AccessToken {
    private @JsonProperty("access_token") String accessToken;
    private @JsonProperty("refresh_token") String refreshToken;
    private @JsonProperty("token_type") String type;
    private @JsonProperty("created_at") Long createdAt;
    private @JsonProperty("expires_in") Long expiresIn;
  }

  /**
   * Generates a secure random string to serve as state parameter and stores in session
   *
   * @return
   */
  protected String generateState() {
    String state = SecureStringUtils.getSecureRandomAlphanumeric(10);
    SessionAttributeUtils.setSessionAttribute(SessionAttributeUtils.RS_OAUTH_STATE, state);
    return state;
  }

  /**
   * Verifies incoming state parameter matches session value
   *
   * @param request
   */
  protected void verifyStateParameter(HttpServletRequest request) {
    String state = request.getParameter("state");
    String originalState = extractCachedOAuth2State();
    if (originalState != null && (state == null || !state.equals(originalState))) {
      throw new IllegalStateException("The OAuth2 'state' parameter is missing or doesn't match.");
    }
  }

  private String extractCachedOAuth2State() {
    String state = (String) getSessionAttribute(SessionAttributeUtils.RS_OAUTH_STATE);
    removeSessionAttribute(SessionAttributeUtils.RS_OAUTH_STATE);
    return state;
  }

  protected String doGetAccessToken(Principal subject, String providerName) {
    return userConnectionManager
        .findByUserNameProviderName(subject.getName(), providerName)
        .map(UserConnection::getAccessToken)
        .orElse("");
  }
}
