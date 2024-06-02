package com.researchspace.webapp.integrations.wopi;

import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates an incoming WOPI request based on access token.<br>
 * Adds User to request as 'user' attribute for use in Controllers.
 */
public class WopiProofKeyValidationInterceptor implements HandlerInterceptor {

  protected static final String ACCESS_TOKEN_PARAM_NAME = "access_token";

  @Autowired private IPropertyHolder propertyHolder;

  @Autowired private WopiProofKeyValidator proofKeyValidator;

  @Value("${msoffice.wopi.redirect.server.url}")
  private String msOfficeWopiServerUrlOverride;

  @Value("${msoffice.wopi.proofKey.validation.enabled}")
  private String proofKeyValidationEnabled;

  protected static final Logger log =
      LoggerFactory.getLogger(WopiProofKeyValidationInterceptor.class);

  @Override
  public boolean preHandle(
      HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
      throws IOException {

    String accessToken = request.getParameter(ACCESS_TOKEN_PARAM_NAME);
    boolean requestProofValid = validateRequestProof(request, accessToken);
    if (!requestProofValid) {
      log.warn("incorrect proof request headers");
      response.setStatus(500);
      return false;
    }
    return true;
  }

  // verifies that request originates from wopi client
  private boolean validateRequestProof(HttpServletRequest req, String accessToken) {
    String wopiServerUrlOverride;
    if (propertyHolder.isMsOfficeEnabled()) {
      wopiServerUrlOverride = msOfficeWopiServerUrlOverride;
    } else {
      wopiServerUrlOverride = propertyHolder.getServerUrl();
    }
    if (!wopiServerUrlOverride.startsWith("http")) {
      wopiServerUrlOverride = propertyHolder.getServerUrl();
    }
    if ("false".equals(proofKeyValidationEnabled)) {
      return true; // skip validation
    }

    String urlWithParams = req.getRequestURL().append('?').append(req.getQueryString()).toString();
    if (wopiServerUrlOverride.startsWith("http")) {
      // override url with deployment property one
      urlWithParams =
          wopiServerUrlOverride + urlWithParams.substring(urlWithParams.indexOf("/wopi/"));
    }

    String proofHeader = req.getHeader("X-WOPI-Proof");
    String oldProofHeader = req.getHeader("X-WOPI-ProofOld");
    String timestampHeader = req.getHeader("X-WOPI-TimeStamp");

    return proofKeyValidator.verifyWopiRequest(
        accessToken, urlWithParams, proofHeader, oldProofHeader, timestampHeader);
  }

  /*
   * ===================
   *    for tests
   * ===================
   */
  void setProofKeyValidationEnabled(String proofKeyValidationEnabled) {
    this.proofKeyValidationEnabled = proofKeyValidationEnabled;
  }
}
