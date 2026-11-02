package com.researchspace.model.collection.types.runtime;

import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.Operator;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Set;

/** ISO date and time strings compare chronologically using their stored text. */
abstract class RuntimeIsoTextType extends RuntimeStoredTextType {
  private final DateTimeFormatter format;

  RuntimeIsoTextType(String pattern) {
    format = DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
  }

  @Override
  public String jsonType() {
    return "string";
  }

  @Override
  public Set<Operator> operators() {
    return ORDERED_OPERATORS;
  }

  @Override
  public FieldComparisonStrategy comparisonStrategy() {
    return FieldComparisonStrategy.SCALAR;
  }

  @Override
  protected Object parseValue(String value) {
    try {
      format.parse(value);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("Runtime field value has an invalid format", ex);
    }
    return value;
  }
}
