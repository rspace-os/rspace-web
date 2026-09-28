package com.researchspace.model.collection;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A writable field whose complete JSON value is decoded as one structured value.
 *
 * <p>Deliberately opt-in: a generic {@link CollectionFieldTypes#array()} stays read-only. The
 * document parser still checks the JSON kind first, then passes the node here instead of reading it
 * as text. Structured fields are never filterable or sortable.
 *
 * @param <V> the value type used by the persistent model
 */
public interface StructuredFieldType<V> extends CollectionFieldType<V> {

  /** Decodes one complete value, throwing {@link IllegalArgumentException} if it is invalid. */
  V parse(JsonNode value);
}
