package com.researchspace.api.v1.controller;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class AbstractThrottleInterceptor implements HandlerInterceptor {

  String assertApiAccess(HttpServletRequest request) {
    return assertApiAccess(request, null);
  }

  String assertApiAccess(HttpServletRequest request, Object handler) {
    if (isPublicApiHandler(handler)) {
      return "publicApiUser:" + request.getRemoteAddr();
    }
    String identifier = request.getHeader("apiKey");
    if (isEmpty(identifier)) {
      identifier = request.getHeader("Authorization");
      if (isEmpty(identifier)) {
        identifier = "anonymousApiUser";
      }
    }
    return identifier;
  }

  private boolean isPublicApiHandler(Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return false;
    }
    return handlerMethod.hasMethodAnnotation(PublicApi.class)
        || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PublicApi.class);
  }
}
