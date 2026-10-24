package com.researchspace.model.collection;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Applies field and relationship read policies to collection requests. */
final class CollectionAccessEvaluator<T> {
  private final Map<String, ResourceMember<T>> fields;
  private final Map<String, FilterSelector<T>> publicSelectors;
  private final String idField;

  CollectionAccessEvaluator(
      Map<String, ResourceMember<T>> fields,
      Map<String, FilterSelector<T>> publicSelectors,
      String idField) {
    this.fields = fields;
    this.publicSelectors = publicSelectors;
    this.idField = idField;
  }

  boolean fieldReadable(String field, AccessContext context) {
    ResourceMember<T> member = fields.get(field);
    if (member instanceof Field<T, ?> described) {
      return idField.equals(field) || described.readAccess().allowsField(context);
    }
    if (member instanceof Relationship<T> relationship) {
      return relationship.readAccess().allowsField(context);
    }
    FilterSelector<T> selector = publicSelectors.get(field);
    if (selector instanceof FilterSelector.RelationshipPart<?> relationshipPart) {
      return relationshipPart.relationship().readAccess().allowsField(context);
    }
    return false;
  }

  Set<String> unreadableFields(AccessContext context) {
    Set<String> unreadable = new LinkedHashSet<>();
    for (ResourceMember<T> member : fields.values()) {
      if (member instanceof Field<?, ?> && !fieldReadable(member.name(), context)) {
        unreadable.add(member.name());
      }
    }
    for (ResourceMember<T> member : fields.values()) {
      if (member instanceof Relationship<?> && !fieldReadable(member.name(), context)) {
        unreadable.add(member.name());
      }
    }
    return unreadable;
  }
}
