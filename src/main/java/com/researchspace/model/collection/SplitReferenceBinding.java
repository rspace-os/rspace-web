package com.researchspace.model.collection;

import java.util.Objects;
import java.util.function.Function;

/** Binds one reference to its persistent ID and, for polymorphic references, kind properties. */
public record SplitReferenceBinding<T, K, ID>(
    Function<T, ResourceReference<K, ID>> reader, String kindProperty, String idProperty) {

  public SplitReferenceBinding {
    Objects.requireNonNull(reader, "Reference reader");
    if (kindProperty != null) {
      kindProperty = requireProperty(kindProperty, "Kind property");
    }
    idProperty = requireProperty(idProperty, "ID property");
  }

  /** Creates a binding whose relationship target is fixed rather than stored on every row. */
  public static <T, ID> SplitReferenceBinding<T, String, ID> monomorphic(
      Function<T, ResourceReference<String, ID>> reader, String idProperty) {
    return new SplitReferenceBinding<>(reader, null, idProperty);
  }

  public ResourceReference<K, ID> read(T entity) {
    return reader.apply(entity);
  }

  public boolean hasKindProperty() {
    return kindProperty != null;
  }

  private static String requireProperty(String property, String label) {
    if (property == null || property.isBlank()) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    return property;
  }
}
