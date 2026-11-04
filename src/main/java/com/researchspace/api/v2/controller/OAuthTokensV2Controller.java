package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.model.ApiV2UiToken;
import com.researchspace.auth.BrowserSessionAuthContext;
import com.researchspace.service.OAuthTokenManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
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
      @RequestAttribute(name = ApiV2Caller.REQUEST_ATTRIBUTE) ApiV2Caller caller,
      HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    String sessionContext = BrowserSessionAuthContext.currentOrCreate(session);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new ApiV2UiToken(
                tokenManager.createUiToken(caller.subject(), caller.actor(), sessionContext)));
  }
}
