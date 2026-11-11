package com.researchspace.model.collection.types;

import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.Operator;
import java.util.Map;
import java.util.Set;

/** Server-rendered objects that cannot be written, filtered or sorted. */
public final class OpaqueObjectFieldType implements CollectionFieldType<Map<String, Object>> {
  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public Class<Map<String, Object>> javaType() {
    return (Class) Map.class;
  }

  @Override
  public InputKind inputKind() {
    return InputKind.OBJECT;
  }

  @Override
  public Map<String, Object> parse(String value) {
    throw new UnsupportedOperationException("Structured read-only fields cannot be parsed");
  }

  @Override
  public Object serialize(Map<String, Object> value) {
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
