package com.researchspace.model.collection.types.runtime;

/** A runtime time validated as a strict hour and minute. */
public final class RuntimeTimeType extends RuntimeIsoTextType {
  public RuntimeTimeType() {
    super("HH:mm");
  }
}
