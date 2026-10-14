package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.throttling.APIRequestThrottler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;

/** Bounds unauthenticated and invalid-credential work by trusted client network source. */
public final class ApiV2PreAuthenticationThrottlingInterceptor
    extends ApiV2AbstractThrottleInterceptor {

  private final APIRequestThrottler throttler;

  public ApiV2PreAuthenticationThrottlingInterceptor(APIRequestThrottler throttler) {
    this.throttler = Objects.requireNonNull(throttler, "Pre-authentication throttler");
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    return throttler.proceed(assertApiAccess(request));
  }
}
