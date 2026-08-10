package com.researchspace.model.collection;

import com.researchspace.model.collection.CollectionDescription.Operator;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.function.Function;

/** Standard scalar field types for collection descriptions. */
public final class CollectionFieldTypes {

  private static final Set<Operator> ORDERED_OPERATORS =
      Set.of(
          Operator.EQUAL,
          Operator.NOT_EQUAL,
          Operator.GREATER_THAN,
          Operator.GREATER_THAN_OR_EQUAL,
          Operator.LESS_THAN,
          Operator.LESS_THAN_OR_EQUAL,
          Operator.IN,
          Operator.NOT_IN);
  private static final Set<Operator> TEXT_OPERATORS =
      Set.of(
          Operator.EQUAL,
          Operator.NOT_EQUAL,
          Operator.IN,
          Operator.NOT_IN,
          Operator.CONTAINS,
          Operator.LIKE,
          Operator.EXISTS);
  private static final Set<Operator> BOOLEAN_OPERATORS =
      Set.of(Operator.EQUAL, Operator.NOT_EQUAL, Operator.EXISTS);

  private static final CollectionFieldType<Long> LONG =
      new ScalarFieldType<>(
          Long.class,
          CollectionFieldType.InputKind.NUMBER,
          new CollectionFieldType.Schema("integer", "int64", null),
          Long::valueOf,
          value -> value,
          ORDERED_OPERATORS,
          false);
  private static final CollectionFieldType<Date> INSTANT =
      new ScalarFieldType<>(
          Date.class,
          CollectionFieldType.InputKind.STRING,
          new CollectionFieldType.Schema("string", "date-time", null),
          value -> Date.from(Instant.parse(value)),
          value -> value.toInstant().toString(),
          ORDERED_OPERATORS,
          false);
  private static final CollectionFieldType<Boolean> BOOLEAN =
      new ScalarFieldType<>(
          Boolean.class,
          CollectionFieldType.InputKind.BOOLEAN,
          new CollectionFieldType.Schema("boolean", null, null),
          value -> {
            if (!"true".equals(value) && !"false".equals(value)) {
              throw new IllegalArgumentException("Boolean value must be true or false");
            }
            return Boolean.valueOf(value);
          },
          value -> value,
          BOOLEAN_OPERATORS,
          false);

  private CollectionFieldTypes() {}

  public static CollectionFieldType<Long> longNumber() {
    return LONG;
  }

  public static CollectionFieldType<Date> instant() {
    return INSTANT;
  }

  public static CollectionFieldType<Boolean> bool() {
    return BOOLEAN;
  }

  public static CollectionFieldType<String> text() {
    return text(Integer.MAX_VALUE);
  }

  public static CollectionFieldType<String> text(int maxLength) {
    if (maxLength < 0) {
      throw new IllegalArgumentException("Text maximum length must not be negative");
    }
    return new ScalarFieldType<>(
        String.class,
        CollectionFieldType.InputKind.STRING,
        new CollectionFieldType.Schema(
            "string", null, maxLength == Integer.MAX_VALUE ? null : maxLength),
        value -> {
          if (value.length() > maxLength) {
            throw new IllegalArgumentException("Text value exceeds its maximum length");
          }
          return value;
        },
        value -> value,
        TEXT_OPERATORS,
        true);
  }

  private record ScalarFieldType<V>(
      Class<V> javaType,
      InputKind inputKind,
      Schema schema,
      Function<String, V> parser,
      Function<V, Object> serializer,
      Set<Operator> operators,
      boolean supportsWildcards)
      implements CollectionFieldType<V> {

    private ScalarFieldType {
      operators = Set.copyOf(operators);
    }

    @Override
    public V parse(String value) {
      return parser.apply(value);
    }

    @Override
    public Object serialize(V value) {
      return serializer.apply(value);
    }
  }
}
