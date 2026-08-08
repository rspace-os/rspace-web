package com.researchspace.api.v2.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Sets permissive CORS headers for REST API v2. */
public class ApiV2PermissiveCorsInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    response.setHeader("Access-Control-Allow-Origin", "*");
    if (RequestMethod.OPTIONS.name().equals(request.getMethod())) {
      response.setHeader("Access-Control-Allow-Methods", "POST, PATCH, GET, OPTIONS, DELETE");
      response.setHeader("Access-Control-Allow-Headers", "apiKey, Authorization, Content-Type");
      response.setHeader("Access-Control-Max-Age", "3600");
      response.setStatus(HttpServletResponse.SC_OK);
      return false;
    }
    return true;
  }
}
