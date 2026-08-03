package com.researchspace.model.collection;

import java.util.Objects;
import java.util.function.Function;

/** Binds one reference to separate persistent kind and ID properties. */
public record SplitReferenceBinding<T, K, ID>(
    Function<T, ResourceReference<K, ID>> reader, String kindProperty, String idProperty) {

  public SplitReferenceBinding {
    Objects.requireNonNull(reader, "Reference reader");
    kindProperty = requireProperty(kindProperty, "Kind property");
    idProperty = requireProperty(idProperty, "ID property");
  }

  public ResourceReference<K, ID> read(T entity) {
    return reader.apply(entity);
  }

  private static String requireProperty(String property, String label) {
    if (property == null || property.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return property;
  }
}
