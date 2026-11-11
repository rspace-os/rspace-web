package com.researchspace.model.collection;

import com.researchspace.model.collection.types.runtime.RuntimeNumberType;
import java.math.BigDecimal;
import java.util.Set;

/** Stable wire identifiers for runtime fields whose values share a text storage column. */
public enum RuntimeFieldValueType {
  TEXT,
  NUMBER,
  DATE,
  TIME,
  RADIO,
  CHOICE;

  public static final int MAX_VALUE_LENGTH = RuntimeFieldType.MAX_VALUE_LENGTH;
  public static final int MAX_NUMBER_PRECISION = RuntimeNumberType.MAX_NUMBER_PRECISION;
  public static final int MAX_NUMBER_SCALE = RuntimeNumberType.MAX_NUMBER_SCALE;
  public static final int MAX_NUMBER_INTEGER_DIGITS = RuntimeNumberType.MAX_NUMBER_INTEGER_DIGITS;
  public static final String NUMBER_PATTERN = RuntimeNumberType.NUMBER_PATTERN;

  public String jsonType() {
    return implementation().jsonType();
  }

  public Set<Operator> operators() {
    return implementation().operators();
  }

  public boolean supportsWildcards() {
    return implementation().supportsWildcards();
  }

  public FieldComparisonStrategy comparisonStrategy() {
    return implementation().comparisonStrategy();
  }

  /** Validates a filter literal and returns the value compared by the query compiler. */
  public Object parse(String value) {
    return implementation().parse(value);
  }

  /** Converts a persisted value into its JSON shape, treating empty values as absent. */
  public Object serialize(String stored) {
    return implementation().serialize(stored);
  }

  /** Parses a number using the same bounds as the SQL numeric conversion. */
  public static BigDecimal parseNumber(String value) {
    return RuntimeNumberType.parseNumber(value);
  }

  private RuntimeFieldType implementation() {
    return RuntimeFieldTypeRegistry.builtIns().require(this);
  }
}
