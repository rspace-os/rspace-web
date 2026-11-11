package com.researchspace.service.resourceaccess;

import com.researchspace.model.resourceaccess.ResourceRoleSource;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Immutable effective role, capabilities, and all contributing sources for one subject. */
public record ResolvedResourceAccess(
    Optional<String> effectiveRole,
    Set<String> capabilities,
    List<ResourceRoleSource> roleSources) {

  public ResolvedResourceAccess {
    effectiveRole = effectiveRole == null ? Optional.empty() : effectiveRole;
    capabilities = Set.copyOf(capabilities);
    roleSources = List.copyOf(roleSources);
  }

  public static ResolvedResourceAccess none() {
    return new ResolvedResourceAccess(Optional.empty(), Set.of(), List.of());
  }

  public boolean hasCapability(String capability) {
    return capabilities.contains(capability);
  }
}
