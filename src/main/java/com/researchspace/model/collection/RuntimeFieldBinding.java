package com.researchspace.model.collection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * How a provider's values are reached from a collection row, in persistence-neutral terms.
 *
 * <p>Every name here is server-owned: the provider chooses the entity, the correlation property and
 * the value property, and a request supplies only values and a stable definition ID. That is what
 * lets the query compiler build a parameterized predicate without ever putting request text into a
 * query path.
 *
 * <p>There are no Blaze-Persistence types here. The query layer interprets this record; a second
 * provider for another storage model supplies its own entity and property names and needs no change
 * to the compiler, because "values live in a child table keyed by parent and definition" is the
 * shape both models share. What is provider-specific — which rows are visible, which definitions
 * exist, how a value is loaded in bulk — stays behind {@link RuntimeCollectionFields}.
 *
 * @param valueEntityType the persistent entity holding one value
 * @param parentIdProperty property of that entity pointing at the collection row's ID
 * @param valueProperty property of that entity holding the stored value
 * @param match further equality conditions identifying this definition's rows, such as the
 *     definition ID and a not-deleted flag
 */
public record RuntimeFieldBinding(
    Class<?> valueEntityType,
    String parentIdProperty,
    String valueProperty,
    Map<String, Object> match) {

  public RuntimeFieldBinding {
    Objects.requireNonNull(valueEntityType, "Runtime field value entity type");
    parentIdProperty = requireProperty(parentIdProperty, "parent ID property");
    valueProperty = requireProperty(valueProperty, "value property");
    Objects.requireNonNull(match, "Runtime field match conditions");
    Map<String, Object> copy = new LinkedHashMap<>();
    match.forEach(
        (property, value) -> copy.put(requireProperty(property, "match property"), value));
    match = Collections.unmodifiableMap(copy);
  }

  private static String requireProperty(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Runtime field " + label + " must not be blank");
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      boolean permitted =
          Character.isLetterOrDigit(character) || character == '_' || character == '.';
      if (!permitted) {
        throw new IllegalArgumentException("Runtime field " + label + " must be a property path");
      }
    }
    return value;
  }
}
