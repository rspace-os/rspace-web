package com.researchspace.webapp.integrations.helper;

import static com.researchspace.session.SessionAttributeUtils.removeSessionAttribute;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.webapp.controller.BaseController;
import jakarta.servlet.http.HttpServletRequest;
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
    private @JsonProperty("id_token") String idToken;
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
    String state = SecureStringUtils.getSecureRandomAlphanumeric(32);
    SessionAttributeUtils.setSessionAttribute(SessionAttributeUtils.RS_OAUTH_STATE, state);
    return state;
  }

  /**
   * Verifies incoming state parameter matches session value
   *
   * @param request
   */
  protected void verifyStateParameter(HttpServletRequest request) {
    verifyStateParameter(request.getParameter("state"));
  }

  protected void verifyStateParameter(String state) {
    String originalState = extractCachedOAuth2State();
    if (originalState == null
        || originalState.isEmpty()
        || state == null
        || !state.equals(originalState)) {
      throw new IllegalStateException(getText("connect.authorizationError.stateMismatch"));
    }
  }

  private String extractCachedOAuth2State() {
    return (String) removeSessionAttribute(SessionAttributeUtils.RS_OAUTH_STATE);
  }
}
