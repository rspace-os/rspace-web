package com.researchspace.model.collection.types.runtime;

/** A runtime calendar date validated as a strict ISO date. */
public final class RuntimeDateType extends RuntimeIsoTextType {
  public RuntimeDateType() {
    super("uuuu-MM-dd");
  }
}
