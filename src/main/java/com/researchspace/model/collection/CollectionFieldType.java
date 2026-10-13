package com.researchspace.model.collection;

import java.util.Map;
import java.util.Set;

/**
 * Defines one collection field's wire type and derives its query and serialization behavior.
 *
 * @param <V> the value type used by the persistent model
 */
public interface CollectionFieldType<V> {

  /** JSON scalar shape accepted when the field is written. */
  enum InputKind {
    STRING,
    NUMBER,
    BOOLEAN,
    OBJECT,
    ARRAY
  }

  /**
   * Introspectable wire schema used by validation and generated API metadata.
   *
   * @param structure additional JSON Schema keywords describing a structured value, such as {@code
   *     items}; empty for scalars
   */
  record Schema(String jsonType, String format, Integer maxLength, Map<String, Object> structure) {

    public Schema(String jsonType, String format, Integer maxLength) {
      this(jsonType, format, maxLength, Map.of());
    }

    public Schema {
      if (jsonType == null || jsonType.isBlank()) {
        throw new IllegalArgumentException("JSON type must not be blank");
      }
      if (maxLength != null && maxLength < 0) {
        throw new IllegalArgumentException("Maximum length must not be negative");
      }
      structure =
          structure == null || structure.isEmpty()
              ? Map.of()
              : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(structure));
    }
  }

  Class<V> javaType();

  InputKind inputKind();

  default Schema schema() {
    return switch (inputKind()) {
      case STRING -> new Schema("string", null, null);
      case NUMBER -> new Schema("number", null, null);
      case BOOLEAN -> new Schema("boolean", null, null);
      case OBJECT -> new Schema("object", null, null);
      case ARRAY -> new Schema("array", null, null);
    };
  }

  V parse(String value);

  Object serialize(V value);

  /**
   * Checks a parsed value against rules that apply only to written values and defaults, throwing
   * {@link IllegalArgumentException} when it breaks one. {@code where} literals share {@link
   * #parse} but never reach this, so a rule here must not be published on filter schemas.
   */
  default void validate(V value) {}

  /**
   * JSON Schema keywords for the rules {@link #validate} enforces, published on body properties.
   */
  default Map<String, Object> writeConstraints() {
    return Map.of();
  }

  Set<Operator> operators();

  default boolean supportsWildcards() {
    return false;
  }

  default boolean sortable() {
    return true;
  }
}
