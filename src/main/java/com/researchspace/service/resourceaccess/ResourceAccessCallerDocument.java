package com.researchspace.service.resourceaccess;

import com.researchspace.model.resourceaccess.ResourceRoleSource;
import java.util.List;
import java.util.Optional;

/** Caller-specific access information returned with the complete assignment document. */
public record ResourceAccessCallerDocument(
    Optional<String> effectiveRole,
    List<ResourceRoleSource> roleSources,
    ResourceAccessCallerCapabilities capabilities,
    /**
     * The caller's own grantee key, so a client can identify which assignment row is theirs. This
     * reveals nothing the caller does not already know about themselves, unlike the grantee
     * identity omitted from direct and implicit {@link ResourceRoleSource}s.
     */
    String granteeKey) {

  public ResourceAccessCallerDocument {
    effectiveRole = effectiveRole == null ? Optional.empty() : effectiveRole;
    roleSources = List.copyOf(roleSources);
  }
}
