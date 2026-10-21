package com.researchspace.model.collection;

import java.util.Objects;

/**
 * A runtime field the current actor may use, with everything the request needs to act on it.
 *
 * <p>Only a provider produces this. Holding one is the proof that the selector was resolved against
 * this actor, which is why the parser, the query compiler and the renderer all take it rather than
 * a selector string: nothing downstream can reintroduce an unauthorized field. Resolution grants
 * access to the definition, not every row containing a value.
 *
 * @param readResource additional row-read policy required when a target delegates to another
 *     resource; null when the relationship's own policy already governs the values
 */
public record ResolvedRuntimeField(
    RuntimeFieldDefinition definition, RuntimeFieldBinding binding, String readResource) {

  public ResolvedRuntimeField(RuntimeFieldDefinition definition, RuntimeFieldBinding binding) {
    this(definition, binding, null);
  }

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
