package com.researchspace.model.collection;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Static, introspectable documentation attached to an {@link AccessFunction}.
 *
 * <p>{@code name} identifies the policy in published documentation, for example as an OpenAPI
 * component, so every declaration that shares a name must describe the same policy.
 */
public record AccessDocumentation(
    String name,
    String description,
    Set<String> denialReasonCodes,
    @JsonProperty("authentication") AuthenticationRequirement authenticationRequirement) {

  private static final Pattern NAME = Pattern.compile("[A-Z][A-Za-z0-9]*");

  public enum AuthenticationRequirement {
    AUTHENTICATED,
    PUBLIC
  }

  public AccessDocumentation(String name, String description, Set<String> denialReasonCodes) {
    this(name, description, denialReasonCodes, AuthenticationRequirement.AUTHENTICATED);
  }

  public AccessDocumentation {
    if (name == null || !NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Access name must be PascalCase: " + name);
    }
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("Access description must not be blank");
    }
    denialReasonCodes = Set.copyOf(Objects.requireNonNull(denialReasonCodes));
    Objects.requireNonNull(authenticationRequirement, "Authentication requirement");
    if (denialReasonCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
      throw new IllegalArgumentException("Denial reason codes must not be blank");
    }
  }
}
