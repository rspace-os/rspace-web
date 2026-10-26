package com.researchspace.model.collection.types.runtime;

import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RuntimeFieldType;
import java.util.Set;

/** Common filter limits and presence rules for runtime values stored as text. */
abstract class RuntimeStoredTextType implements RuntimeFieldType {
  protected static final Set<Operator> ORDERED_OPERATORS =
      Set.of(
          Operator.EQUAL,
          Operator.NOT_EQUAL,
          Operator.GREATER_THAN,
          Operator.GREATER_THAN_OR_EQUAL,
          Operator.LESS_THAN,
          Operator.LESS_THAN_OR_EQUAL,
          Operator.IN,
          Operator.NOT_IN,
          Operator.EXISTS);

  @Override
  public boolean supportsWildcards() {
    return false;
  }

  @Override
  public final Object parse(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Runtime field value must not be null");
    }
    if (value.length() > MAX_VALUE_LENGTH) {
      throw new IllegalArgumentException("Runtime field value is too long");
    }
    return parseValue(value);
  }

  @Override
  public final Object serialize(String stored) {
    return stored == null || stored.isEmpty() ? null : serializeValue(stored);
  }

  protected Object parseValue(String value) {
    return value;
  }

  protected Object serializeValue(String stored) {
    return stored;
  }
}
