package com.researchspace.model.collection.types;

import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.Operator;
import java.util.Set;

/** Column-backed boolean values. */
public final class BooleanFieldType implements CollectionFieldType<Boolean> {
  @Override
  public Class<Boolean> javaType() {
    return Boolean.class;
  }

  @Override
  public InputKind inputKind() {
    return InputKind.BOOLEAN;
  }

  @Override
  public Schema schema() {
    return new Schema("boolean", null, null);
  }

  @Override
  public Boolean parse(String value) {
    if (!"true".equals(value) && !"false".equals(value)) {
      throw new IllegalArgumentException("Boolean value must be true or false");
    }
    return Boolean.valueOf(value);
  }

  @Override
  public Object serialize(Boolean value) {
    return value;
  }

  @Override
  public Set<Operator> operators() {
    return TypeOperators.BOOLEAN;
  }
}
