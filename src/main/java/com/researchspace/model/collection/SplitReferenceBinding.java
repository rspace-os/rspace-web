package com.researchspace.model.collection;

import java.util.Objects;
import java.util.function.Function;

/** Binds one reference to its persistent ID and, for polymorphic references, kind properties. */
public record SplitReferenceBinding<T, K, ID>(
    Function<T, ResourceReference<K, ID>> reader, String kindProperty, String idProperty) {

  public SplitReferenceBinding {
    Objects.requireNonNull(reader, "Reference reader");
    if (kindProperty != null) {
      kindProperty = CollectionDescription.requireText(kindProperty, "Kind property");
    }
    idProperty = CollectionDescription.requireText(idProperty, "ID property");
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
}
