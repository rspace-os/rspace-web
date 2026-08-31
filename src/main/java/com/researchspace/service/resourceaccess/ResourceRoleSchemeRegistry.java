package com.researchspace.service.resourceaccess;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Validated lookup of the role scheme registered for each protected resource type. */
@Component
public class ResourceRoleSchemeRegistry {

  private final Map<String, ResourceRoleScheme> schemes;

  public ResourceRoleSchemeRegistry(Collection<ResourceRoleScheme> schemes) {
    Objects.requireNonNull(schemes, "schemes");
    Map<String, ResourceRoleScheme> byKey = new LinkedHashMap<>();
    for (ResourceRoleScheme scheme : schemes) {
      Objects.requireNonNull(scheme, "scheme").validate();
      ResourceRoleScheme previous = byKey.putIfAbsent(scheme.key(), scheme);
      if (previous != null) {
        throw new IllegalArgumentException("Duplicate resource role scheme key: " + scheme.key());
      }
    }
    this.schemes = Map.copyOf(byKey);
  }

  /** Returns the registered scheme or fails closed for corrupt or unsupported persisted data. */
  public ResourceRoleScheme getRequired(String schemeKey) {
    ResourceRoleScheme scheme = schemes.get(schemeKey);
    if (scheme == null) {
      throw new IllegalArgumentException("Unknown resource role scheme: " + schemeKey);
    }
    return scheme;
  }
}
