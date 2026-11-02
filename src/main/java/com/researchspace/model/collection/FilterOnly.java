package com.researchspace.model.collection;

/** A public query member omitted from resource documents and writable fields. */
public record FilterOnly<T>(FilterSelector<T> selector) implements ResourceMember<T> {
  @Override
  public String name() {
    return selector.name();
  }
}
