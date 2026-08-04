package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Authenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates protected REST API v2 handlers and adds their user request attribute.
 *
 * <p>Generic CRUD handlers defer authorization to their collection's access functions. For those
 * handlers this interceptor resolves an identity only when API credentials are present; a request
 * carrying only browser session cookies proceeds with a null user and is allowed or rejected at the
 * resource registration. The {@link ApiV2Access} annotation defines the mode for every handler.
 *
 * <p>Unlike v1, v2 does not log into or out of Shiro. API credentials have already been validated
 * by their managers, and avoiding Shiro ensures an ambient browser session cannot influence the
 * request or be destroyed as a side effect.
 */
@RequiredArgsConstructor
public class ApiV2AuthenticationInterceptor implements HandlerInterceptor {

  private final ApiV2Authenticator apiV2Authenticator;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    ApiV2Access.Mode mode = ApiV2AccessResolver.mode(handler);
    if (mode == ApiV2Access.Mode.PUBLIC) {
      return true;
    }
    if (mode == ApiV2Access.Mode.RESOURCE_POLICY) {
      apiV2Authenticator
          .authenticateIfPresent(request)
          .ifPresent(user -> request.setAttribute("user", user));
      return true;
    }
    request.setAttribute("user", apiV2Authenticator.authenticate(request));
    return true;
  }
}
