package com.researchspace.model.collection;

import java.util.Objects;
import java.util.Set;

/** Static, introspectable documentation attached to an {@link AccessFunction}. */
public record AccessDocumentation(
    String description,
    Set<String> denialReasonCodes,
    AuthenticationRequirement authenticationRequirement) {

  public enum AuthenticationRequirement {
    AUTHENTICATED,
    PUBLIC
  }

  public AccessDocumentation(String description, Set<String> denialReasonCodes) {
    this(description, denialReasonCodes, AuthenticationRequirement.AUTHENTICATED);
  }

  public AccessDocumentation {
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
