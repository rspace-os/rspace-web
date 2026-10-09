package com.researchspace.model.collection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Reads and writes entities according to a collection description. */
final class CollectionEntityMapper<T> {
  private final Map<String, Field<T, ?>> fields;
  private final String idField;

  CollectionEntityMapper(List<? extends Field<T, ?>> fields, String idField) {
    this.fields = new LinkedHashMap<>();
    fields.forEach(field -> this.fields.put(field.name(), field));
    this.idField = idField;
  }

  Map<String, Object> toDocument(T entity) {
    return toDocument(entity, field -> true);
  }

  Map<String, Object> toDocument(T entity, Predicate<String> selection) {
    return toDocument(entity, selection, Map.of());
  }

  Map<String, Object> toDocument(
      T entity, Predicate<String> selection, Map<String, Object> readOverrides) {
    Objects.requireNonNull(entity, "Entity");
    Objects.requireNonNull(selection, "Selection");
    Objects.requireNonNull(readOverrides, "Read overrides");
    Map<String, Object> document = new LinkedHashMap<>();
    fields.values().stream()
        .filter(field -> selection.test(field.name()))
        .forEach(
            field ->
                document.put(
                    field.name(),
                    readOverrides.containsKey(field.name())
                        ? readOverrides.get(field.name())
                        : field.documentValue(entity)));
    return document;
  }

  Object idValue(T entity) {
    Objects.requireNonNull(entity, "Entity");
    Field<T, ?> field = fields.get(idField);
    if (field == null) {
      throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
    }
    return field.documentValue(entity);
  }

  void apply(T entity, Map<String, Object> values, WriteOperation operation) {
    Objects.requireNonNull(entity, "Entity");
    Objects.requireNonNull(values, "Values");
    values.forEach(
        (name, value) -> {
          Field<T, ?> field = fields.get(name);
          if (field == null || !field.writableOn(operation)) {
            throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
          }
          field.validateValue(value);
        });
    fields.values().stream()
        .filter(field -> field.writableOn(operation) && values.containsKey(field.name()))
        .forEach(field -> field.write(entity, values.get(field.name())));
  }

  Object readRelationship(T entity, Relationship<T> relationship) {
    return relationship.read(entity);
  }

  Optional<Object> relationshipTargetId(T entity, Relationship<T> relationship) {
    Object value = readRelationship(entity, relationship);
    return value instanceof ResourceReference<?, ?> reference
        ? Optional.ofNullable(reference.id())
        : Optional.empty();
  }
}
