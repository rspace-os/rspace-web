package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.model.ApiV2UiToken;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.session.SessionAttributeUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/oauth/tokens")
public class OAuthTokensV2Controller {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private final OAuthTokenManager tokenManager;

  @PostMapping
  @Operation(
      operationId = "createUiOAuthToken",
      summary = "Create a UI OAuth token",
      description = "Creates an OAuth token from the authenticated browser session.",
      responses = {
        @ApiResponse(responseCode = "200", description = "UI OAuth token."),
        @ApiResponse(responseCode = "401", description = "A browser session is required."),
        @ApiResponse(responseCode = "429", description = "The request was throttled.")
      })
  public ResponseEntity<ApiV2UiToken> createToken(
      @RequestAttribute(name = "user") User user, HttpServletRequest request) {
    if (request.getSession(false) != null
        && Boolean.TRUE.equals(
            request.getSession(false).getAttribute(SessionAttributeUtils.IS_RUN_AS))) {
      SECURITY_LOG.warn(
          "Refused REST API v2 UI token creation while operating as user [{}]", user.getUsername());
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(new ApiV2UiToken(tokenManager.createUiToken(user)));
  }
}
