package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.throttling.Bucket4jApiRequestThrottler;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.User;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Derives the throttle bucket key for a REST API v2 request.
 *
 * <p>This runs before authentication. Consequently, supplied credentials are not a trustworthy
 * identity: an attacker can rotate arbitrary values to obtain fresh buckets. Pre-authentication
 * admission is therefore keyed only by the network source. Retention is bounded by {@link
 * Bucket4jApiRequestThrottler}.
 */
public class ApiV2AbstractThrottleInterceptor implements HandlerInterceptor {

  String assertApiAccess(HttpServletRequest request) {
    Object authenticated = request.getAttribute("user");
    if (authenticated instanceof User user && user.getId() != null) {
      return fingerprint("authenticatedApiUser", user.getId().toString());
    }
    // RequestUtil.remoteAddr honours X-Forwarded-For, so deployments must accept that header only
    // from trusted proxies. The value is hashed to avoid retaining client addresses in memory.
    return clientFingerprint("preAuthApiClient", request);
  }

  private static String clientFingerprint(String prefix, HttpServletRequest request) {
    return fingerprint(prefix, RequestUtil.remoteAddr(request));
  }

  private static String fingerprint(String prefix, String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return prefix + ":" + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }
}
