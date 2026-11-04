package com.researchspace.model.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.Operator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A role-source grantee ID is a numeric resource ID or the ALL_USERS audience key. */
final class GranteeIdFieldType implements CollectionFieldType<Object> {

  @Override
  public Class<Object> javaType() {
    return Object.class;
  }

  @Override
  public InputKind inputKind() {
    return InputKind.STRING;
  }

  @Override
  public Schema schema() {
    return new Schema(
        "integer",
        null,
        null,
        Map.of(
            "type",
            List.of("integer", "string"),
            "oneOf",
            List.of(
                Map.of("type", "integer", "format", "int64"),
                Map.of("type", "string", "enum", List.of("ALL_USERS")))));
  }

  @Override
  public boolean accepts(JsonNode value) {
    return value.isIntegralNumber() && value.canConvertToLong()
        || value.isTextual() && "ALL_USERS".equals(value.textValue());
  }

  @Override
  public Object parse(String value) {
    return "ALL_USERS".equals(value) ? value : Long.valueOf(value);
  }

  @Override
  public Object parseScalar(JsonNode value) {
    return value.isIntegralNumber() ? value.longValue() : parse(value.textValue());
  }

  @Override
  public Object serialize(Object value) {
    return value;
  }

  @Override
  public Set<Operator> operators() {
    return Set.of();
  }

  @Override
  public boolean sortable() {
    return false;
  }
}
