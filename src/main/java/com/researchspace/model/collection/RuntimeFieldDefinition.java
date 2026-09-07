package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.Operator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One runtime field a caller may name, as published by the actor-scoped catalog.
 *
 * <p>Identity is {@code id}, a stable global identifier owned by the provider. It is deliberately
 * not the label: a label can be renamed, duplicated across sources, or reused with a different
 * type, so merging by name would silently combine unrelated concepts and would break a saved view
 * on the next rename.
 *
 * @param id stable identifier, such as a Global ID
 * @param selector the public {@code namespace.id} name a caller uses in {@code where} and {@code
 *     fields}
 * @param label display name, which may repeat across definitions
 * @param type wire behaviour of this field's values
 * @param sourceId identifier of the definition's owner, such as a template
 * @param sourceLabel display name of that owner, so duplicate labels can be told apart
 * @param options selectable values for a radio or choice field, otherwise empty
 */
public record RuntimeFieldDefinition(
    String id,
    String selector,
    String label,
    RuntimeFieldValueType type,
    String sourceId,
    String sourceLabel,
    List<String> options) {

  public RuntimeFieldDefinition {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Runtime field id must not be blank");
    }
    if (selector == null || selector.isBlank()) {
      throw new IllegalArgumentException("Runtime field selector must not be blank");
    }
    Objects.requireNonNull(type, "Runtime field type");
    label = label == null ? "" : label;
    options = options == null ? List.of() : List.copyOf(options);
  }

  public Set<Operator> operators() {
    return type.operators();
  }

  public boolean supportsWildcards() {
    return type.supportsWildcards();
  }

  /**
   * Whether this field's values can be requested as a column.
   *
   * <p>Every currently supported type can be. Link, attachment and reference fields are not
   * published at all until their summary wire shape is agreed, so there is no type here that is
   * filterable but not projectable.
   */
  public boolean columnSelectable() {
    return true;
  }
}
