package com.researchspace.model.collection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

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
  private static final CollectionFieldType<Integer> INTEGER =
      new ScalarFieldType<>(
          Integer.class,
          CollectionFieldType.InputKind.NUMBER,
          new CollectionFieldType.Schema("integer", "int32", null),
          Integer::valueOf,
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

  public static CollectionFieldType<Integer> integer() {
    return INTEGER;
  }

  public static CollectionFieldType<Date> instant() {
    return INSTANT;
  }

  public static CollectionFieldType<Boolean> bool() {
    return BOOLEAN;
  }

  /** A server-rendered object field that is readable but never queryable or writable. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static CollectionFieldType<Map<String, Object>> object() {
    return new OpaqueFieldType<>((Class) Map.class, CollectionFieldType.InputKind.OBJECT);
  }

  /** A server-rendered array field that is readable but never queryable or writable. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static <E> CollectionFieldType<List<E>> array() {
    return new OpaqueFieldType<>((Class) List.class, CollectionFieldType.InputKind.ARRAY);
  }

  public static CollectionFieldType<String> text() {
    return text(Integer.MAX_VALUE);
  }

  /**
   * A field stored as an enum constant name.
   *
   * <p>Written for an internal filter, where a server-built constraint compares against the enum
   * value itself. Equality is the only meaningful comparison, so ordering and wildcards are absent.
   * The schema lists the constant names, because {@link CollectionFieldType#parse} rejects any
   * other value everywhere, filters included.
   */
  public static <E extends Enum<E>> CollectionFieldType<E> enumeration(Class<E> type) {
    Objects.requireNonNull(type, "Enum type");
    List<String> names = Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    return new ScalarFieldType<>(
        type,
        CollectionFieldType.InputKind.STRING,
        new CollectionFieldType.Schema("string", null, null, Map.of("enum", names)),
        value -> Enum.valueOf(type, value),
        Enum::name,
        BOOLEAN_OPERATORS,
        false);
  }

  public static CollectionFieldType<String> text(int maxLength) {
    if (maxLength < 0) {
      throw new IllegalArgumentException("Text maximum length must not be negative");
    }
    // Checked in parse too: filters rely on it for where literals, which never reach validate.
    Consumer<String> checkLength =
        value -> {
          if (value.length() > maxLength) {
            throw new IllegalArgumentException("Text value exceeds its maximum length");
          }
        };
    return new ScalarFieldType<>(
        String.class,
        CollectionFieldType.InputKind.STRING,
        new CollectionFieldType.Schema(
            "string", null, maxLength == Integer.MAX_VALUE ? null : maxLength),
        value -> {
          checkLength.accept(value);
          return value;
        },
        value -> value,
        TEXT_OPERATORS,
        true,
        checkLength);
  }

  /**
   * Adds write-only rules to a scalar type. They are checked on writes and defaults and published
   * on body properties only, because {@code where} literals are never checked against them.
   *
   * <p>One decorator holds all of a field's rules, so wrapping a constrained type is rejected, as
   * is an opaque type, which cannot be written at all.
   */
  public static <V> CollectionFieldType<V> constrained(
      CollectionFieldType<V> base, Constraints constraints) {
    Objects.requireNonNull(base, "Constrained field type");
    Objects.requireNonNull(constraints, "Field constraints");
    if (base instanceof ConstrainedFieldType<?> || base instanceof OpaqueFieldType<?>) {
      throw new IllegalArgumentException("Constraints require a single scalar field type");
    }
    if ((constraints.minimum() != null || constraints.maximum() != null)
        && base.inputKind() != CollectionFieldType.InputKind.NUMBER) {
      throw new IllegalArgumentException("Minimum and maximum require a number field");
    }
    if ((constraints.minLength() != null
            || constraints.pattern() != null
            || constraints.allowed() != null)
        && base.javaType() != String.class) {
      throw new IllegalArgumentException(
          "Minimum length, pattern and allowed values require a text field");
    }
    return new ConstrainedFieldType<>(base, constraints);
  }

  /**
   * Write-only rules for one field.
   *
   * @param pattern matched with {@link java.util.regex.Matcher#find()}, so it is unanchored like a
   *     JSON Schema {@code pattern}; it must have no flags, which the published pattern cannot
   *     carry
   * @param allowed the permitted values, or {@code null} when any value is allowed
   */
  public record Constraints(
      BigDecimal minimum,
      BigDecimal maximum,
      Integer minLength,
      Pattern pattern,
      List<String> allowed) {

    public Constraints {
      if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
        throw new IllegalArgumentException("Minimum must not exceed maximum");
      }
      if (minLength != null && minLength < 0) {
        throw new IllegalArgumentException("Minimum length must not be negative");
      }
      if (pattern != null && pattern.flags() != 0) {
        throw new IllegalArgumentException("Pattern flags cannot be published");
      }
      if (allowed != null) {
        allowed = List.copyOf(allowed);
        if (allowed.isEmpty() || allowed.stream().anyMatch(String::isBlank)) {
          throw new IllegalArgumentException("Allowed values must not be empty or blank");
        }
      }
    }

    public boolean isEmpty() {
      return minimum == null
          && maximum == null
          && minLength == null
          && pattern == null
          && allowed == null;
    }
  }

  private record ScalarFieldType<V>(
      Class<V> javaType,
      InputKind inputKind,
      Schema schema,
      Function<String, V> parser,
      Function<V, Object> serializer,
      Set<Operator> operators,
      boolean supportsWildcards,
      Consumer<V> validator)
      implements CollectionFieldType<V> {

    private ScalarFieldType {
      operators = Set.copyOf(operators);
    }

    private ScalarFieldType(
        Class<V> javaType,
        InputKind inputKind,
        Schema schema,
        Function<String, V> parser,
        Function<V, Object> serializer,
        Set<Operator> operators,
        boolean supportsWildcards) {
      this(
          javaType,
          inputKind,
          schema,
          parser,
          serializer,
          operators,
          supportsWildcards,
          value -> {});
    }

    @Override
    public void validate(V value) {
      validator.accept(value);
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

  private record ConstrainedFieldType<V>(CollectionFieldType<V> base, Constraints constraints)
      implements CollectionFieldType<V> {

    @Override
    public Class<V> javaType() {
      return base.javaType();
    }

    @Override
    public InputKind inputKind() {
      return base.inputKind();
    }

    @Override
    public Schema schema() {
      return base.schema();
    }

    @Override
    public V parse(String value) {
      return base.parse(value);
    }

    @Override
    public Object serialize(V value) {
      return base.serialize(value);
    }

    @Override
    public Set<Operator> operators() {
      return base.operators();
    }

    @Override
    public boolean supportsWildcards() {
      return base.supportsWildcards();
    }

    @Override
    public boolean sortable() {
      return base.sortable();
    }

    @Override
    public void validate(V value) {
      base.validate(value);
      if (constraints.minimum() != null || constraints.maximum() != null) {
        BigDecimal number = new BigDecimal(value.toString());
        if (constraints.minimum() != null && number.compareTo(constraints.minimum()) < 0) {
          throw new IllegalArgumentException("Value is below its minimum");
        }
        if (constraints.maximum() != null && number.compareTo(constraints.maximum()) > 0) {
          throw new IllegalArgumentException("Value is above its maximum");
        }
      }
      String text = String.valueOf(base.serialize(value));
      // Counted in code points, as JSON Schema counts characters.
      if (constraints.minLength() != null
          && text.codePointCount(0, text.length()) < constraints.minLength()) {
        throw new IllegalArgumentException("Text value is shorter than its minimum length");
      }
      if (constraints.pattern() != null && !constraints.pattern().matcher(text).find()) {
        throw new IllegalArgumentException("Text value does not match its pattern");
      }
      if (constraints.allowed() != null && !constraints.allowed().contains(text)) {
        throw new IllegalArgumentException("Value is not one of the allowed values");
      }
    }

    @Override
    public Map<String, Object> writeConstraints() {
      Map<String, Object> keywords = new LinkedHashMap<>();
      if (constraints.minimum() != null) {
        keywords.put("minimum", constraints.minimum());
      }
      if (constraints.maximum() != null) {
        keywords.put("maximum", constraints.maximum());
      }
      if (constraints.minLength() != null) {
        keywords.put("minLength", constraints.minLength());
      }
      if (constraints.pattern() != null) {
        keywords.put("pattern", constraints.pattern().pattern());
      }
      if (constraints.allowed() != null) {
        keywords.put("enum", constraints.allowed());
      }
      return Collections.unmodifiableMap(keywords);
    }
  }

  private record OpaqueFieldType<V>(Class<V> javaType, InputKind inputKind)
      implements CollectionFieldType<V> {

    @Override
    public V parse(String value) {
      throw new UnsupportedOperationException("Structured read-only fields cannot be parsed");
    }

    @Override
    public Object serialize(V value) {
      return value;
    }

    @Override
    public Set<Operator> operators() {
      return Set.of();
    }

    @Override
    public boolean sortable() {
      return false;
    }
  }
}
