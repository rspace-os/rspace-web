package com.researchspace.api.v2.resource;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Version field and singular mutations requiring an If-Match precondition. */
public record VersionPolicy(
    Kind kind,
    Optional<String> field,
    Optional<String> missingCode,
    Set<ResourceOperation> appliesTo) {
  public enum Kind {
    NONE,
    OPTIONAL,
    REQUIRED
  }

  public static final VersionPolicy NONE =
      new VersionPolicy(Kind.NONE, Optional.empty(), Optional.empty(), Set.of());

  public VersionPolicy {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(field);
    Objects.requireNonNull(missingCode);
    appliesTo = Set.copyOf(appliesTo);
    if ((kind == Kind.NONE) != field.isEmpty()
        || field.filter(String::isBlank).isPresent()
        || (kind == Kind.REQUIRED) != missingCode.isPresent()
        || missingCode.filter(String::isBlank).isPresent()
        || (kind == Kind.REQUIRED) == appliesTo.isEmpty()
        || (kind == Kind.REQUIRED && !appliesTo.contains(ResourceOperation.UPDATE))
        || !Set.of(ResourceOperation.UPDATE, ResourceOperation.DELETE).containsAll(appliesTo)) {
      throw new IllegalArgumentException("Inconsistent mutation version policy");
    }
  }

  public static VersionPolicy optional(String field) {
    return new VersionPolicy(Kind.OPTIONAL, Optional.of(field), Optional.empty(), Set.of());
  }

  public static VersionPolicy required(
      String field, String missingCode, ResourceOperation... operations) {
    return new VersionPolicy(
        Kind.REQUIRED, Optional.of(field), Optional.of(missingCode), Set.of(operations));
  }

  public boolean requires(ResourceOperation operation) {
    return appliesTo.contains(operation);
  }
}
