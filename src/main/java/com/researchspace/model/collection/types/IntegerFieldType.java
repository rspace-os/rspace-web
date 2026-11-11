package com.researchspace.model.collection.types;

import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.Operator;
import java.util.Set;

/** Column-backed integer values. */
public final class IntegerFieldType implements CollectionFieldType<Integer> {
  @Override
  public Class<Integer> javaType() {
    return Integer.class;
  }

  @Override
  public InputKind inputKind() {
    return InputKind.NUMBER;
  }

  @Override
  public Schema schema() {
    return new Schema("integer", "int32", null);
  }

  @Override
  public Integer parse(String value) {
    return Integer.valueOf(value);
  }

  @Override
  public Object serialize(Integer value) {
    return value;
  }

  @Override
  public Set<Operator> operators() {
    return TypeOperators.ORDERED;
  }
}
