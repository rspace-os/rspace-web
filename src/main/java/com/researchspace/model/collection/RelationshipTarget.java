package com.researchspace.model.collection;

import java.util.Objects;

/** Maps one stored relationship kind to its public REST resource. */
public record RelationshipTarget<K>(
    String resourceName, K storedKind, String globalIdPrefix, Class<?> entityType) {

  public RelationshipTarget {
    if (resourceName == null || resourceName.isBlank()) {
      throw new IllegalArgumentException("Target resource name must not be blank");
    }
    Objects.requireNonNull(storedKind, "Stored relationship kind");
    if (globalIdPrefix != null && globalIdPrefix.isBlank()) {
      throw new IllegalArgumentException("Global ID prefix must not be blank");
    }
    Objects.requireNonNull(entityType, "Target entity type");
  }

  public RelationshipTarget(String resourceName, K storedKind, Class<?> entityType) {
    this(resourceName, storedKind, null, entityType);
  }
}
