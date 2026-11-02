package com.researchspace.model.collection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Reads and writes entities according to a collection description. */
final class CollectionEntityMapper<T> {
  private final Map<String, ResourceMember<T>> fields;
  private final String idField;

  CollectionEntityMapper(Map<String, ResourceMember<T>> fields, String idField) {
    this.fields = fields;
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
    return toDocument(
        entity,
        selection,
        readOverrides,
        new AccessContext(null, AccessContext.Operation.READ, "embedded"));
  }

  Map<String, Object> toDocument(
      T entity,
      Predicate<String> selection,
      Map<String, Object> readOverrides,
      AccessContext context) {
    Objects.requireNonNull(entity, "Entity");
    Objects.requireNonNull(selection, "Selection");
    Objects.requireNonNull(readOverrides, "Read overrides");
    Map<String, Object> document = new LinkedHashMap<>();
    for (ResourceMember<T> member : fields.values()) {
      if (member instanceof Field<T, ?> field
          && selection.test(field.name())
          && (idField.equals(field.name()) || field.readAccess().allowsField(context))) {
        document.put(
            field.name(),
            readOverrides.containsKey(field.name())
                ? field.projectReadValue(readOverrides.get(field.name()), context)
                : field.documentValue(entity, context));
      }
    }
    return document;
  }

  Object idValue(T entity) {
    Objects.requireNonNull(entity, "Entity");
    ResourceMember<T> member = fields.get(idField);
    if (member instanceof Field<T, ?> field) {
      return field.documentValue(entity);
    }
    throw new IllegalStateException("ID field must be described");
  }

  void apply(T entity, Map<String, Object> values, WriteOperation operation) {
    Objects.requireNonNull(entity, "Entity");
    Objects.requireNonNull(values, "Values");
    values.forEach(
        (name, value) -> {
          ResourceMember<T> member = fields.get(name);
          if (!(member instanceof Field<T, ?> field) || !field.writableOn(operation)) {
            throw new CollectionQueryException(CollectionQueryException.Reason.FIELD);
          }
          field.validateValue(value);
        });
    for (ResourceMember<T> member : fields.values()) {
      if (member instanceof Field<T, ?> field
          && field.writableOn(operation)
          && values.containsKey(field.name())) {
        field.write(entity, values.get(field.name()));
      }
    }
  }

  Optional<Object> relationshipTargetId(T entity, Relationship<T> relationship) {
    Object value = relationship.read(entity);
    return value instanceof ResourceReference<?, ?> reference
        ? Optional.ofNullable(reference.id())
        : Optional.empty();
  }
}
