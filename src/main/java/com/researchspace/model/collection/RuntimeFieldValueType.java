package com.researchspace.model.collection;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.collection.CollectionDescription.Operator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Wire behaviour of one runtime field, independent of where its values are stored.
 *
 * <p>A runtime field has no {@link CollectionFieldType} because it has no column of its own: its
 * values live in a child value table whose single text column serves every declared type. This enum
 * is the explicit mapping the plan requires between a declared field type, the JSON shape a client
 * sees, the operators it may use, and how a stored string becomes a typed value.
 *
 * <p>{@link #DATE} and {@link #TIME} deliberately compare as text. Both are persisted in a
 * zero-padded ISO form ({@code yyyy-MM-dd}, {@code HH:mm}) whose lexicographic order is its
 * chronological order, so an ordered comparison is type-correct without a conversion that would
 * defeat an index and fail on a malformed row.
 */
public enum RuntimeFieldValueType {
  TEXT(
      "string",
      Set.of(
          Operator.EQUAL,
          Operator.NOT_EQUAL,
          Operator.IN,
          Operator.NOT_IN,
          Operator.CONTAINS,
          Operator.LIKE,
          Operator.EXISTS),
      true),

  NUMBER(
      "number",
      Set.of(
          Operator.EQUAL,
          Operator.NOT_EQUAL,
          Operator.GREATER_THAN,
          Operator.GREATER_THAN_OR_EQUAL,
          Operator.LESS_THAN,
          Operator.LESS_THAN_OR_EQUAL,
          Operator.IN,
          Operator.NOT_IN,
          Operator.EXISTS),
      false),

  DATE("string", orderedTextOperators(), false),

  TIME("string", orderedTextOperators(), false),

  RADIO(
      "string",
      Set.of(Operator.EQUAL, Operator.NOT_EQUAL, Operator.IN, Operator.NOT_IN, Operator.EXISTS),
      false),

  CHOICE("array", Set.of(Operator.CONTAINS, Operator.IN, Operator.EXISTS), false);

  public static final int MAX_VALUE_LENGTH = 1000;

  /** Maximum precision supported by MariaDB's DECIMAL conversion for numeric runtime fields. */
  public static final int MAX_NUMBER_PRECISION = 65;

  /** Maximum scale supported by MariaDB's DECIMAL conversion for numeric runtime fields. */
  public static final int MAX_NUMBER_SCALE = 30;

  private static final int MAX_NUMBER_INTEGER_DIGITS = MAX_NUMBER_PRECISION - MAX_NUMBER_SCALE;

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

  private final String jsonType;
  private final Set<Operator> operators;
  private final boolean wildcards;

  RuntimeFieldValueType(String jsonType, Set<Operator> operators, boolean wildcards) {
    this.jsonType = jsonType;
    this.operators = operators;
    this.wildcards = wildcards;
  }

  private static Set<Operator> orderedTextOperators() {
    return Set.of(
        Operator.EQUAL,
        Operator.NOT_EQUAL,
        Operator.GREATER_THAN,
        Operator.GREATER_THAN_OR_EQUAL,
        Operator.LESS_THAN,
        Operator.LESS_THAN_OR_EQUAL,
        Operator.IN,
        Operator.NOT_IN,
        Operator.EXISTS);
  }

  public String jsonType() {
    return jsonType;
  }

  public Set<Operator> operators() {
    return operators;
  }

  public boolean supportsWildcards() {
    return wildcards;
  }

  /**
   * Validates a filter argument and returns the value the query compiler compares against.
   *
   * <p>Every type but {@link #NUMBER} compares as text, because that is how the value is stored.
   * Validation still happens, so {@code =ge=} on a date cannot smuggle in an unbounded string.
   *
   * @throws IllegalArgumentException if the argument is not valid for this type
   */
  public Object parse(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Runtime field value must not be null");
    }
    if (value.length() > MAX_VALUE_LENGTH) {
      throw new IllegalArgumentException("Runtime field value is too long");
    }
    return switch (this) {
      case NUMBER -> parseNumber(value);
      case DATE -> requireFormat(value, DATE_FORMAT, true);
      case TIME -> requireFormat(value, TIME_FORMAT, false);
      case TEXT, RADIO, CHOICE -> value;
    };
  }

  /**
   * Converts one persisted value into its JSON shape.
   *
   * <p>An empty string is normalised to {@code null}: the storage cannot tell "written then
   * cleared" from "never written", and the documented contract is that neither has a value.
   */
  public Object serialize(String stored) {
    if (stored == null || stored.isEmpty()) {
      return null;
    }
    return switch (this) {
      case NUMBER -> parseNumberOrNull(stored);
      case CHOICE -> choiceOptions(stored);
      case TEXT, DATE, TIME, RADIO -> stored;
    };
  }

  /** Parses a number using the same bounds as the SQL numeric conversion. */
  public static BigDecimal parseNumber(String value) {
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

  private static Object parseNumberOrNull(String stored) {
    try {
      return parseNumber(stored);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static List<String> choiceOptions(String stored) {
    String trimmed = stored.trim();
    if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
      return List.of(stored);
    }
    String[] options = JacksonUtil.fromJson(trimmed, String[].class);
    if (options == null) {
      throw new IllegalArgumentException("Couldn't convert stored choice options to an array");
    }
    return Arrays.asList(options);
  }

  private static String requireFormat(String value, DateTimeFormatter format, boolean date) {
    try {
      if (date) {
        LocalDate.parse(value, format);
      } else {
        LocalTime.parse(value, format);
      }
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("Runtime field value has an invalid format", ex);
    }
    return value;
  }
}
