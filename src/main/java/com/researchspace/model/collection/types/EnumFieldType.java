package com.researchspace.model.collection.types;

import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.Operator;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Enum literals use their constant names on the wire and enum values in queries. */
public record EnumFieldType<E extends Enum<E>>(Class<E> javaType)
    implements CollectionFieldType<E> {
  public EnumFieldType {
    Objects.requireNonNull(javaType, "Enum type");
  }

  @Override
  public InputKind inputKind() {
    return InputKind.STRING;
  }

  @Override
  public Schema schema() {
    return new Schema(
        "string",
        null,
        null,
        Map.of("enum", Arrays.stream(javaType.getEnumConstants()).map(Enum::name).toList()));
  }

  @Override
  public E parse(String value) {
    return Enum.valueOf(javaType, value);
  }

  @Override
  public Object serialize(E value) {
    return value.name();
  }

  @Override
  public Set<Operator> operators() {
    return TypeOperators.BOOLEAN;
  }
}
