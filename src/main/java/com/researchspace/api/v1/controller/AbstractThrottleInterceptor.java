package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;

public class AbstractThrottleInterceptor implements HandlerInterceptor {

  String assertApiAccess(HttpServletRequest request) {
    String identifier = request.getHeader("apiKey");
    if (isEmpty(identifier)) {
      identifier = request.getHeader("Authorization");
      if (isEmpty(identifier)) {
        identifier = "anonymousApiUser";
      }
    }
    return identifier;
  }
}
