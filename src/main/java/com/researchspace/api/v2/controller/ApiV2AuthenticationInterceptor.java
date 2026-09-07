package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Authenticator;
import com.researchspace.api.v2.auth.ApiV2BrowserSessionAuthenticator;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2AuthenticationMode;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.model.permissions.SecurityLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the REST API v2 caller and lets the endpoint policy decide whether it is required.
 *
 * <p>A request carrying only browser session cookies proceeds with a null user. Invalid supplied
 * API credentials still fail before policy evaluation.
 *
 * <p>Unlike v1, v2 does not log into or out of Shiro. External API credentials remain stateless.
 * Signed UI credentials must match the effective subject, original actor, and rotating browser
 * session context. The existing Shiro run-as identity can then participate.
 */
@RequiredArgsConstructor
public class ApiV2AuthenticationInterceptor implements HandlerInterceptor {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private final ApiV2Authenticator apiV2Authenticator;
  private final ApiV2BrowserSessionAuthenticator browserSessionAuthenticator;
  private final ApiV2EndpointCatalog endpoints;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // A preflight never carries credentials: a browser sends it before it knows whether it may send
    // the request at all, and it strips Authorization and custom headers such as apiKey. Running
    // authentication over one refuses every cross-origin write before the real request is made.
    if (CorsUtils.isPreFlightRequest(request)) {
      return true;
    }
    ApiV2Caller caller =
        (endpoints.authenticationMode(handler) == ApiV2AuthenticationMode.BROWSER_SESSION
                ? browserSessionAuthenticator.authenticateIfPresent(request)
                : apiV2Authenticator.authenticateIfPresent(request))
            .orElse(null);
    var subject = caller == null ? null : caller.subject();
    endpoints.authorize(request, handler, subject);
    if (caller != null) {
      request.setAttribute(ApiV2Caller.REQUEST_ATTRIBUTE, caller);
      if (caller.isDelegated()) {
        SECURITY_LOG.info(
            "REST API v2 delegated request [{} {}], actor [{}], subject [{}]",
            request.getMethod(),
            request.getRequestURI(),
            caller.actor().getUsername(),
            subject.getUsername());
      }
      response.setHeader(
          HttpHeaders.CACHE_CONTROL, CacheControl.noStore().cachePrivate().getHeaderValue());
    }
    return true;
  }
}
