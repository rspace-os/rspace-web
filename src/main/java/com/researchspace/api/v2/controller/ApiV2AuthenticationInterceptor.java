package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Authenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authenticates protected REST API v2 handlers and adds their user request attribute.
 *
 * <p>Generic CRUD handlers defer authorization to their collection's access functions. For those
 * handlers this interceptor resolves an identity only when credentials or a reusable browser
 * session are present; a genuinely anonymous request proceeds with a null user and is allowed or
 * rejected at the resource registration. All other handlers require authentication unless marked
 * {@link PublicApiV2}.
 *
 * <p>Deviation from v1: v1 logs out unconditionally in {@code postHandle}, which destroys a browser
 * session that authentication had merely reused. v2 logs out only sessions it created itself, which
 * it detects via {@link ApiV2Authenticator#SESSION_REUSED_ATTRIBUTE}. Doing this in {@code
 * afterCompletion} rather than {@code postHandle} means the session is also released when the
 * handler throws.
 */
@RequiredArgsConstructor
public class ApiV2AuthenticationInterceptor implements HandlerInterceptor {

  private final ApiV2Authenticator apiV2Authenticator;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (isPublic(handler)) {
      return true;
    }
    if (isDescriptionControlled(handler)) {
      apiV2Authenticator
          .authenticateIfPresent(request)
          .ifPresent(user -> request.setAttribute("user", user));
      return true;
    }
    request.setAttribute("user", apiV2Authenticator.authenticate(request));
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (!isPublic(handler) && request.getAttribute("user") != null && !sessionWasReused(request)) {
      apiV2Authenticator.logout();
    }
  }

  private static boolean isDescriptionControlled(Object handler) {
    return handler instanceof HandlerMethod handlerMethod
        && ApiV2CrudController.class.isAssignableFrom(handlerMethod.getBeanType());
  }

  private static boolean sessionWasReused(HttpServletRequest request) {
    return Boolean.TRUE.equals(request.getAttribute(ApiV2Authenticator.SESSION_REUSED_ATTRIBUTE));
  }

  private static boolean isPublic(Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return false;
    }
    return handlerMethod.hasMethodAnnotation(PublicApiV2.class)
        || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PublicApiV2.class);
  }
}
