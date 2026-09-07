package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Presence-aware, typed create or update document. */
public record ParsedDocument(WriteOperation operation, Map<String, Object> values) {

  public ParsedDocument {
    values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  /** Creates a presence-aware update document from already parsed values. */
  public static ParsedDocument update(Map<String, Object> values) {
    return new ParsedDocument(WriteOperation.UPDATE, values);
  }

  public boolean changed(String field) {
    return values.containsKey(field);
  }
}
