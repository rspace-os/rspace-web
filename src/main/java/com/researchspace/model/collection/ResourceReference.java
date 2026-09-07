package com.researchspace.model.collection;

import java.util.Objects;

/** The persistent kind and identifier of one related resource. */
public record ResourceReference<K, ID>(K kind, ID id) {

  public ResourceReference {
    Objects.requireNonNull(kind, "Reference kind");
    Objects.requireNonNull(id, "Reference id");
  }
}
