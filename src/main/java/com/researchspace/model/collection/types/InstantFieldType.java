package com.researchspace.model.collection.types;

import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.Operator;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/** Column-backed date values. */
public final class InstantFieldType implements CollectionFieldType<Date> {
  @Override
  public Class<Date> javaType() {
    return Date.class;
  }

  @Override
  public InputKind inputKind() {
    return InputKind.STRING;
  }

  @Override
  public Schema schema() {
    return new Schema("string", "date-time", null);
  }

  @Override
  public Date parse(String value) {
    return Date.from(Instant.parse(value));
  }

  @Override
  public Object serialize(Date value) {
    return value.toInstant().toString();
  }

  @Override
  public Set<Operator> operators() {
    return TypeOperators.ORDERED;
  }
}
