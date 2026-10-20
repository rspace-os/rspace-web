package com.researchspace.model.collection.types;

import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.Operator;
import java.util.Set;

/** Column-backed long values. */
public final class LongFieldType implements CollectionFieldType<Long> {
  @Override
  public Class<Long> javaType() {
    return Long.class;
  }

  @Override
  public InputKind inputKind() {
    return InputKind.NUMBER;
  }

  @Override
  public Schema schema() {
    return new Schema("integer", "int64", null);
  }

  @Override
  public Long parse(String value) {
    return Long.valueOf(value);
  }

  @Override
  public Object serialize(Long value) {
    return value;
  }

  @Override
  public Set<Operator> operators() {
    return TypeOperators.ORDERED;
  }

  @Override
  public FieldComparisonStrategy comparisonStrategy() {
    return FieldComparisonStrategy.SCALAR;
  }
}
