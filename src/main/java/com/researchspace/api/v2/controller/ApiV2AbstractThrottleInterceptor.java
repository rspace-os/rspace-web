package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.throttling.Bucket4jApiRequestThrottler;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Derives the throttle bucket key for a REST API v2 request.
 *
 * <p>This runs before authentication. Consequently, supplied credentials are not a trustworthy
 * identity: an attacker can rotate arbitrary values to obtain fresh buckets. Pre-authentication
 * admission is therefore keyed only by the network source. Retention is bounded by {@link
 * Bucket4jApiRequestThrottler}.
 */
public abstract class ApiV2AbstractThrottleInterceptor implements HandlerInterceptor {

  String throttleKey(HttpServletRequest request) {
    ApiV2Caller caller = ApiV2Caller.from(request);
    User user = caller == null ? null : caller.actor();
    if (user != null && user.getId() != null) {
      return fingerprint("authenticatedApiUser", user.getId().toString());
    }
    // RequestUtil.remoteAddr honours X-Forwarded-For, so deployments must accept that header only
    // from trusted proxies. The value is hashed to avoid retaining client addresses in memory.
    return fingerprint("preAuthApiClient", RequestUtil.remoteAddr(request));
  }

  private static String fingerprint(String prefix, String value) {
    return prefix + ":" + DigestUtils.sha256Hex(value);
  }
}
