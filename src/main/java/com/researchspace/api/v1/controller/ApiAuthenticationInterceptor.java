package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.auth.ApiAuthenticator;
import com.researchspace.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UrlPathHelper;

/**
 * Authenticates an incoming API request based on API key or OAuth token.<br>
 * Adds User to request as 'user' attribute for use in Controllers. <br>
 * Rejects disabled or inactivated users.
 */
public class ApiAuthenticationInterceptor implements HandlerInterceptor {

  public static final String API_AUTHENTICATED_ATTR = "apiAuthenticated";

  private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();

  @Autowired private ApiAuthenticator combinedApiAuthenticator;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (allowsAnonymousFeatureFlagRequest(request)) {
      return true;
    }
    User user = combinedApiAuthenticator.authenticate(request);
    request.setAttribute("user", user);
    request.setAttribute(API_AUTHENTICATED_ATTR, true);
    return true;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView) {
    if (Boolean.TRUE.equals(request.getAttribute(API_AUTHENTICATED_ATTR))) {
      combinedApiAuthenticator.logout();
    }
  }

  private boolean allowsAnonymousFeatureFlagRequest(HttpServletRequest request) {
    if (request.getHeader("apiKey") != null || request.getHeader("Authorization") != null) {
      return false;
    }
    String lookupPath = URL_PATH_HELPER.getLookupPathForRequest(request);
    return "GET".equals(request.getMethod()) && "/api/v2/feature-flags".equals(lookupPath)
        || isFeatureFlagWritePath(request, lookupPath);
  }

  private boolean isFeatureFlagWritePath(HttpServletRequest request, String path) {
    return ("PUT".equals(request.getMethod()) || "DELETE".equals(request.getMethod()))
        && path.startsWith("/api/v2/feature-flags/");
  }
}
