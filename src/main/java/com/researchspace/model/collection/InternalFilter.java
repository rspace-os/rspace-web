package com.researchspace.model.collection;

import java.util.Objects;

/**
 * A filter the server may compile but a caller may not name.
 *
 * <p>Unlike a {@link Field} this has no reader, so it never appears in a document and cannot be
 * evaluated in memory. It exists so an access constraint can test a persisted property that must
 * stay unpublished.
 */
public record InternalFilter(String name, String property, CollectionFieldType<?> type) {

  public InternalFilter {
    if (name == null || name.isBlank() || property == null || property.isBlank()) {
      throw new IllegalArgumentException("Internal filter names must not be blank");
    }
    Objects.requireNonNull(type, "Internal filter type");
  }
}
