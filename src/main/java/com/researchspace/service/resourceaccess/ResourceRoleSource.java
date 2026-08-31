package com.researchspace.service.resourceaccess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/** One persisted or implicit source contributing to a subject's effective resource access. */
public record ResourceRoleSource(
    ResourceRoleSourceKind kind,
    @JsonProperty("role") String roleKey,
    @JsonIgnore String granteeKey,
    @JsonIgnore String nameSnapshot) {

  public ResourceRoleSource {
    if (kind == null || roleKey == null || roleKey.isBlank()) {
      throw new IllegalArgumentException("Resource role source kind and role must be present");
    }
  }

  public static ResourceRoleSource implicit(String roleKey) {
    return new ResourceRoleSource(ResourceRoleSourceKind.IMPLICIT, roleKey, null, null);
  }

  /** Safe source identity, omitted for direct and implicit sources. */
  @JsonProperty("grantee")
  public Map<String, Object> grantee() {
    if (kind == ResourceRoleSourceKind.DIRECT || kind == ResourceRoleSourceKind.IMPLICIT) {
      return null;
    }
    Map<String, Object> grantee = new LinkedHashMap<>();
    grantee.put("kind", kind.name());
    grantee.put("key", granteeKey);
    if (granteeKey != null && granteeKey.contains(":")) {
      String rawId = granteeKey.substring(granteeKey.indexOf(':') + 1);
      try {
        grantee.put("id", Long.valueOf(rawId));
      } catch (NumberFormatException ignored) {
        grantee.put("id", rawId.toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
      }
    }
    grantee.put("name", nameSnapshot);
    return Map.copyOf(grantee);
  }
}
