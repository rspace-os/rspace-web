package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.controller.ApiV2Access.Mode;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

/** Resolves the authentication mode that all REST API v2 interceptors must use. */
public final class ApiV2AccessResolver {

  private ApiV2AccessResolver() {}

  public static Mode mode(Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return Mode.AUTHENTICATED;
    }
    ApiV2Access methodAccess =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ApiV2Access.class);
    if (methodAccess != null) {
      return methodAccess.value();
    }
    ApiV2Access typeAccess =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiV2Access.class);
    return typeAccess == null ? Mode.AUTHENTICATED : typeAccess.value();
  }
}
