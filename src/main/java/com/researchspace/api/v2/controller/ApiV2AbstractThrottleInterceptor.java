package com.researchspace.api.v2.controller;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.researchspace.api.v2.throttling.BoundedAllowanceTrackerSource;
import com.researchspace.core.util.RequestUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Derives the throttle bucket key for a REST API v2 request.
 *
 * <p>This runs before authentication, so every key here is caller-supplied and untrusted. Retention
 * is bounded by {@link BoundedAllowanceTrackerSource} rather than by trusting the key.
 */
public class ApiV2AbstractThrottleInterceptor implements HandlerInterceptor {

  String assertApiAccess(HttpServletRequest request) {
    String identifier = request.getHeader("apiKey");
    if (!isEmpty(identifier)) {
      return fingerprint("apiKey", identifier);
    }
    identifier = request.getHeader("Authorization");
    if (!isEmpty(identifier)) {
      return fingerprint("authorization", normalizeAuthorization(identifier));
    }
    // RequestUtil.remoteAddr honours X-Forwarded-For, so deployments must accept that header only
    // from trusted proxies. The value is hashed to avoid retaining client addresses in memory.
    return clientFingerprint("anonymousApiUser", request);
  }

  private static String normalizeAuthorization(String authorization) {
    return authorization.trim().replaceAll("\\s+", " ");
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
