package com.researchspace.model.collection.types.runtime;

import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.Operator;
import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;

/** Decimal runtime values using the same syntax and bounds as SQL numeric conversion. */
public final class RuntimeNumberType extends RuntimeStoredTextType {
  public static final int MAX_NUMBER_PRECISION = 65;
  public static final int MAX_NUMBER_SCALE = 30;
  public static final int MAX_NUMBER_INTEGER_DIGITS = MAX_NUMBER_PRECISION - MAX_NUMBER_SCALE;

  // ASCII digits match MariaDB; separated digit runs avoid excessive regex backtracking.
  public static final String NUMBER_PATTERN =
      "[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)([eE][+-]?[0-9]+)?";

  private static final Pattern NUMBER_SYNTAX = Pattern.compile(NUMBER_PATTERN);

  @Override
  public String jsonType() {
    return "number";
  }

  @Override
  public Set<Operator> operators() {
    return ORDERED_OPERATORS;
  }

  @Override
  public FieldComparisonStrategy comparisonStrategy() {
    return FieldComparisonStrategy.NUMERIC_TEXT;
  }

  @Override
  protected Object parseValue(String value) {
    return parseNumber(value);
  }

  @Override
  protected Object serializeValue(String stored) {
    try {
      return parseNumber(stored);
    } catch (IllegalArgumentException ex) {
      // Invalid historical values are absent in projections and numeric SQL comparisons.
      return null;
    }
  }

  /** Parses a decimal without rounding values that MariaDB cannot represent exactly. */
  public static BigDecimal parseNumber(String value) {
    if (!NUMBER_SYNTAX.matcher(value).matches()) {
      throw new IllegalArgumentException("Runtime field value is not a number");
    }
    final BigDecimal number;
    try {
      number = new BigDecimal(value);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Runtime field value is not a number", ex);
    }
    BigDecimal normalised = number.stripTrailingZeros();
    long integerDigits = (long) normalised.precision() - normalised.scale();
    if (integerDigits > MAX_NUMBER_INTEGER_DIGITS
        || normalised.scale() > MAX_NUMBER_SCALE
        || normalised.precision() > MAX_NUMBER_PRECISION) {
      throw new IllegalArgumentException("Runtime field number exceeds supported precision");
    }
    return number;
  }
}
