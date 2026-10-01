package com.researchspace.model.collection;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Applies field and relationship read policies to collection requests. */
final class CollectionAccessEvaluator<T> {
  private final Map<String, Field<T, ?>> fields;
  private final Map<String, Relationship<T>> relationships;
  private final Map<String, FilterSelector<T>> publicSelectors;
  private final String idField;

  CollectionAccessEvaluator(
      Map<String, Field<T, ?>> fields,
      Map<String, Relationship<T>> relationships,
      Map<String, FilterSelector<T>> publicSelectors,
      String idField) {
    this.fields = fields;
    this.relationships = relationships;
    this.publicSelectors = publicSelectors;
    this.idField = idField;
  }

  boolean fieldReadable(String field, AccessContext context) {
    Field<T, ?> described = fields.get(field);
    if (described != null) {
      return idField.equals(field) || described.readAccess().allowsField(context);
    }
    Relationship<T> relationship = relationships.get(field);
    if (relationship != null) {
      return relationship.readAccess().allowsField(context);
    }
    FilterSelector<T> selector = publicSelectors.get(field);
    if (selector instanceof FilterSelector.RelationshipPart<?> relationshipPart) {
      return relationshipPart.relationship().readAccess().allowsField(context);
    }
    return false;
  }

  Set<String> unreadableFields(AccessContext context) {
    return Stream.concat(fields.keySet().stream(), relationships.keySet().stream())
        .filter(name -> !fieldReadable(name, context))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
