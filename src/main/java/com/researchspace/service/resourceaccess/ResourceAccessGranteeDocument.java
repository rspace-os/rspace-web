package com.researchspace.service.resourceaccess;

import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import com.researchspace.model.resourceaccess.ResourceRoleSource;
import java.util.List;
import java.util.Optional;

/** Safe read-side identity and bounded effective-role explanation for one assigned grantee. */
public record ResourceAccessGranteeDocument(
    ResourceGranteeKind kind,
    Object id,
    String key,
    String name,
    String detail,
    boolean available,
    Optional<String> effectiveRole,
    List<ResourceRoleSource> roleSources) {

  public ResourceAccessGranteeDocument {
    effectiveRole = effectiveRole == null ? Optional.empty() : effectiveRole;
    roleSources = List.copyOf(roleSources);
  }
}
