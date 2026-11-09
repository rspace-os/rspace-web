package com.researchspace.api.v2.resource;

import com.researchspace.model.User;
import com.researchspace.model.collection.ResourceRenderer.ResolvedTarget;
import com.researchspace.model.permissions.SecurityLogger;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared fail-closed handling for readable relationship and audit target lookups. */
final class ApiV2ReadableTargetSupport {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private ApiV2ReadableTargetSupport() {}

  static Map<Object, ResolvedTarget> hideAuthorizationFailure(
      User actor, String resourceName, Supplier<Map<Object, ResolvedTarget>> lookup) {
    try {
      return lookup.get();
    } catch (AuthorizationException ex) {
      SECURITY_LOG.warn(
          "REST API v2 relationship target authorization failure for user [{}], resource [{}]",
          actor == null ? "(anonymous)" : actor.getUsername(),
          resourceName,
          ex);
      return Map.of();
    }
  }
}
