package com.researchspace.model.collection;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A writable field whose complete JSON value is decoded as one structured value.
 *
 * <p>The document parser checks the JSON kind before decoding. Array fields additionally implement
 * {@link DeclaredFieldType}, whose child declarations control parsing and output projection.
 * Structured fields are never filterable or sortable.
 *
 * @param <V> the value type used by the persistent model
 */
public interface StructuredFieldType<V> extends CollectionFieldType<V> {

  /** Decodes one complete value, throwing {@link IllegalArgumentException} if it is invalid. */
  V parse(JsonNode value);

  @Override
  default V parseDocument(JsonNode value, WriteOperation operation) {
    return parse(value);
  }
}
