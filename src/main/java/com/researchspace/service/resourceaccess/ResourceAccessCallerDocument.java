package com.researchspace.service.resourceaccess;

import java.util.List;
import java.util.Optional;

/** Caller-specific access information returned with the complete assignment document. */
public record ResourceAccessCallerDocument(
    Optional<String> effectiveRole,
    List<ResourceRoleSource> roleSources,
    ResourceAccessCallerCapabilities capabilities) {

  public ResourceAccessCallerDocument {
    effectiveRole = effectiveRole == null ? Optional.empty() : effectiveRole;
    roleSources = List.copyOf(roleSources);
  }
}
