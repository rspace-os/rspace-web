package com.researchspace.api.v1.controller;

import static org.apache.commons.lang.StringUtils.isEmpty;

import javax.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

public class AbstractThrottleInterceptor extends HandlerInterceptorAdapter {

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
