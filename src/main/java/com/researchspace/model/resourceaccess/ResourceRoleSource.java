package com.researchspace.model.resourceaccess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;

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

  /**
   * Safe source identity for a group or audience source. {@code id} is the numeric or symbolic
   * identifier parsed from the key, and is omitted when the key carries none.
   */
  public record Grantee(
      ResourceRoleSourceKind kind,
      String key,
      @JsonInclude(JsonInclude.Include.NON_NULL)
          @Schema(
              types = {"integer", "string"},
              requiredMode = Schema.RequiredMode.NOT_REQUIRED)
          Object id,
      @Schema(nullable = true) String name) {}

  /** Safe source identity, null for direct and implicit sources in direct JSON serialization. */
  @JsonProperty("grantee")
  @Schema(nullable = true)
  public Grantee grantee() {
    if (kind == ResourceRoleSourceKind.DIRECT || kind == ResourceRoleSourceKind.IMPLICIT) {
      return null;
    }
    String rawId = ResourceGranteeKeys.parseIdentifier(granteeKey);
    Object id = null;
    if (rawId != null) {
      try {
        id = Long.valueOf(rawId);
      } catch (NumberFormatException ignored) {
        id = rawId.toUpperCase(Locale.ROOT).replace('-', '_');
      }
    }
    return new Grantee(kind, granteeKey, id, nameSnapshot);
  }
}
