package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Authenticator;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the REST API v2 caller and lets the endpoint policy decide whether it is required.
 *
 * <p>A request carrying only browser session cookies proceeds with a null user. Invalid supplied
 * API credentials still fail before policy evaluation.
 *
 * <p>Unlike v1, v2 does not log into or out of Shiro. API credentials have already been validated
 * by their managers, and avoiding Shiro ensures an ambient browser session cannot influence the
 * request or be destroyed as a side effect.
 */
@RequiredArgsConstructor
public class ApiV2AuthenticationInterceptor implements HandlerInterceptor {

  private final ApiV2Authenticator apiV2Authenticator;
  private final ApiV2EndpointCatalog endpoints;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    User caller = apiV2Authenticator.authenticateIfPresent(request).orElse(null);
    endpoints.authorize(request, handler, caller);
    if (caller != null) {
      request.setAttribute("user", caller);
    }
    return true;
  }
}
