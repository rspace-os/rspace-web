package com.researchspace.model.collection;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** An embedded value whose declared fields control each supplied object. */
public interface DeclaredFieldType<V> extends StructuredFieldType<V> {

  /** Java type of one object, whether this value is an object or an array of objects. */
  Class<?> rowType();

  /** Constructs one row after every declared child has been parsed and validated. */
  Object assemble(Map<String, Object> values);

  /** Checks rules spanning several children after constructing the row. */
  default void validateRow(Object row) {}

  /** Parses a complete replacement, applying required children on either write operation. */
  default V parse(JsonNode value, WriteOperation operation) {
    return javaType().cast(EmbeddedObjects.parse(this, value, operation));
  }

  /** Serializes complete declared children; the field renderer applies caller access afterward. */
  default Object serialize(V value, AccessContext context) {
    return EmbeddedObjects.defaultDocument(this, value);
  }

  @Override
  default V parse(JsonNode value) {
    return parse(value, WriteOperation.CREATE);
  }

  @Override
  default Object serialize(V value) {
    return serialize(value, new AccessContext(null, AccessContext.Operation.READ, "embedded"));
  }

  @Override
  default V parse(String value) {
    throw new UnsupportedOperationException("Embedded values cannot be parsed from text");
  }
}
