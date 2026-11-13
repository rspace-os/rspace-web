package com.researchspace.model.collection.types;

import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.Operator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A server-rendered object that cannot be written, filtered or sorted. The API's JSON mapper writes
 * the value, and generated API metadata documents it from the same type.
 */
public record ReadOnlyObjectFieldType<R>(Class<R> javaType) implements CollectionFieldType<R> {

  public ReadOnlyObjectFieldType {
    Objects.requireNonNull(javaType, "Read-only object type");
  }

  @Override
  public InputKind inputKind() {
    return InputKind.OBJECT;
  }

  @Override
  public Schema schema() {
    return new Schema("object", null, null, Map.of(), javaType);
  }

  @Override
  public R parse(String value) {
    throw new UnsupportedOperationException("Read-only objects cannot be parsed");
  }

  @Override
  public Object serialize(R value) {
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
