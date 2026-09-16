package com.researchspace.model.collection;

import java.util.Objects;

/**
 * A runtime field the current actor may use, with everything the request needs to act on it.
 *
 * <p>Only a provider produces this. Holding one is the proof that the selector was resolved against
 * this actor, which is why the parser, the query compiler and the renderer all take it rather than
 * a selector string: nothing downstream can reintroduce an unauthorized field.
 */
public record ResolvedRuntimeField(RuntimeFieldDefinition definition, RuntimeFieldBinding binding) {

  public ResolvedRuntimeField {
    Objects.requireNonNull(definition, "Runtime field definition");
    Objects.requireNonNull(binding, "Runtime field binding");
  }

  public String id() {
    return definition.id();
  }

  public String selector() {
    return definition.selector();
  }

  public RuntimeFieldValueType type() {
    return definition.type();
  }
}
