package com.researchspace.api.v1.controller;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.api.v1.auth.ApiAuthenticator;
import com.researchspace.model.User;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * Authenticates an incoming API request based on API key or OAuth token.<br>
 * Adds User to request as 'user' attribute for use in Controllers. <br>
 * Rejects disabled or inactivated users.
 */
public class ApiAuthenticationInterceptor extends HandlerInterceptorAdapter {

  @Autowired private ApiAuthenticator apiAuthenticator;

  @Autowired private AnalyticsManager analyticsMgr;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    User user = apiAuthenticator.authenticate(request);
    request.setAttribute("user", user);
    if (user != null) {
      analyticsMgr.apiUsed(user, request);
    }
    return true;
  }

  /** Logs out. */
  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView) {
    apiAuthenticator.logout();
  }
}
