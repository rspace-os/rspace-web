package com.researchspace.api.v1.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Sets permissive CORS headers to allow cross-origin usage. */
public class ApiPermissiveCorsInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // api access is allowed from any origin
    response.setHeader("Access-Control-Allow-Origin", "*");

    // process OPTIONS preflight requests here
    if (RequestMethod.OPTIONS.name().equals(request.getMethod())) {
      response.setHeader("Access-Control-Allow-Methods", "POST, PUT, GET, OPTIONS, DELETE");
      response.setHeader("Access-Control-Allow-Headers", "apiKey, Authorization, Content-Type");
      response.setHeader("Access-Control-Max-Age", "3600");
      response.setStatus(HttpServletResponse.SC_OK);
      return false; // no need to process further
    }
    // otherwise continue with processing the request
    return true;
  }
}
