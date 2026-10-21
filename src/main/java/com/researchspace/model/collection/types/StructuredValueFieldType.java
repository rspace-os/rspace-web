package com.researchspace.model.collection.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.StructuredFieldType;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Complete JSON values decoded by a server-owned parser. */
public record StructuredValueFieldType<V>(
    Class<V> javaType,
    InputKind inputKind,
    Schema schema,
    Function<JsonNode, V> parser,
    Function<V, Object> serializer)
    implements StructuredFieldType<V> {

  public StructuredValueFieldType {
    if (inputKind == InputKind.ARRAY) {
      throw new IllegalArgumentException("Array fields must declare their children");
    }
    Objects.requireNonNull(javaType, "Structured Java type");
    Objects.requireNonNull(inputKind, "Structured input kind");
    Objects.requireNonNull(schema, "Structured schema");
    Objects.requireNonNull(parser, "Structured parser");
    Objects.requireNonNull(serializer, "Structured serializer");
  }

  @Override
  public V parse(JsonNode value) {
    return parser.apply(value);
  }

  @Override
  public V parse(String value) {
    throw new UnsupportedOperationException("Structured fields cannot be parsed from text");
  }

  @Override
  public Object serialize(V value) {
    return serializer.apply(value);
  }

  @Override
  public Set<Operator> operators() {
    return Set.of();
  }

  @Override
  public FieldComparisonStrategy comparisonStrategy() {
    return FieldComparisonStrategy.NONE;
  }

  @Override
  public boolean sortable() {
    return false;
  }
}
