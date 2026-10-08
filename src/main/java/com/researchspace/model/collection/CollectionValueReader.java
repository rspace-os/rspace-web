package com.researchspace.model.collection;

import java.util.Map;

/** Reads values needed by in-memory filtering and sorting. */
final class CollectionValueReader<T> {
  private final Map<String, Field<T, ?>> fields;
  private final Map<String, FilterSelector<T>> selectors;

  CollectionValueReader(Map<String, Field<T, ?>> fields, Map<String, FilterSelector<T>> selectors) {
    this.fields = fields;
    this.selectors = selectors;
  }

  Object readFilterValue(T entity, String selectorName) {
    FilterSelector<T> selector = selectors.get(selectorName);
    if (selector == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    if (selector instanceof FilterSelector.Property<T> property) {
      Field<T, ?> field = fields.get(property.name());
      if (field == null) {
        throw new IllegalStateException(
            "Internal filter " + property.name() + " cannot be evaluated in memory");
      }
      return field.reader.apply(entity);
    }
    if (selector instanceof FilterSelector.RelationshipPart<T> part) {
      return readRelationshipValue(entity, part);
    }
    if (selector instanceof FilterSelector.RuntimeField<T> runtime) {
      throw new IllegalStateException(
          "Runtime field " + runtime.name() + " cannot be evaluated in memory");
    }
    throw new IllegalStateException("Unsupported filter selector " + selector.getClass());
  }

  private Object readRelationshipValue(T entity, FilterSelector.RelationshipPart<T> selector) {
    Object value = selector.readRelationship(entity);
    if (value == null) {
      return null;
    }
    if (!(value instanceof ResourceReference<?, ?> reference)) {
      throw new IllegalStateException("Relationship filter requires a resource reference");
    }
    return switch (selector.part()) {
      case ROOT -> reference;
      case KIND -> reference.kind();
      case ID -> reference.id();
    };
  }

  Object readSortValue(T entity, String fieldName) {
    Field<T, ?> field = fields.get(fieldName);
    if (field == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    return field.reader.apply(entity);
  }
}
