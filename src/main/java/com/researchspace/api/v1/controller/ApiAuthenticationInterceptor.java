package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.auth.ApiAuthenticator;
import com.researchspace.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Authenticates an incoming API request based on API key or OAuth token.<br>
 * Adds User to request as 'user' attribute for use in Controllers. <br>
 * Rejects disabled or inactivated users.
 */
public class ApiAuthenticationInterceptor implements HandlerInterceptor {

  @Autowired private ApiAuthenticator combinedApiAuthenticator;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    User user = combinedApiAuthenticator.authenticate(request);
    request.setAttribute("user", user);
    return true;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView) {
    combinedApiAuthenticator.logout();
  }
}
