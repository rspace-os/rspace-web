package com.researchspace.model.collection;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.collection.types.BooleanFieldType;
import com.researchspace.model.collection.types.ConstrainedFieldType;
import com.researchspace.model.collection.types.DeclaredArrayFieldType;
import com.researchspace.model.collection.types.DeclaredObjectFieldType;
import com.researchspace.model.collection.types.EnumFieldType;
import com.researchspace.model.collection.types.FieldConstraints;
import com.researchspace.model.collection.types.InstantFieldType;
import com.researchspace.model.collection.types.IntegerFieldType;
import com.researchspace.model.collection.types.LongFieldType;
import com.researchspace.model.collection.types.OpaqueObjectFieldType;
import com.researchspace.model.collection.types.StructuredValueFieldType;
import com.researchspace.model.collection.types.TextFieldType;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Standard scalar and declared embedded field types for collection descriptions. */
public final class CollectionFieldTypes {

  private static final CollectionFieldType<Long> LONG = new LongFieldType();
  private static final CollectionFieldType<Integer> INTEGER = new IntegerFieldType();
  private static final CollectionFieldType<Date> INSTANT = new InstantFieldType();
  private static final CollectionFieldType<Boolean> BOOLEAN = new BooleanFieldType();

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
  public static CollectionFieldType<Map<String, Object>> object() {
    return new OpaqueObjectFieldType();
  }

  /** Declares object rows, whose adapter runs after child validation. */
  public static <R> DeclaredFieldType<List<R>> array(
      Class<R> rowType,
      List<? extends Field<R, ?>> fields,
      Function<Map<String, Object>, R> adapter) {
    return array(rowType, fields, adapter, row -> {}, rows -> {}, 0, Integer.MAX_VALUE);
  }

  /** Declares row and whole-array rules, including published inclusive row bounds. */
  public static <R> DeclaredFieldType<List<R>> array(
      Class<R> rowType,
      List<? extends Field<R, ?>> fields,
      Function<Map<String, Object>, R> adapter,
      Consumer<R> rowValidator,
      Consumer<List<R>> arrayValidator,
      int minRows,
      int maxRows) {
    return new DeclaredArrayFieldType<>(
        rowType, fields, adapter, rowValidator, arrayValidator, minRows, maxRows);
  }

  /** Declares a finite embedded object using the same child definitions as array rows. */
  public static <R> DeclaredFieldType<R> object(
      Class<R> rowType,
      List<? extends Field<R, ?>> fields,
      Function<Map<String, Object>, R> adapter) {
    return object(rowType, fields, adapter, row -> {});
  }

  public static <R> DeclaredFieldType<R> object(
      Class<R> rowType,
      List<? extends Field<R, ?>> fields,
      Function<Map<String, Object>, R> adapter,
      Consumer<R> rowValidator) {
    return new DeclaredObjectFieldType<>(rowType, fields, adapter, rowValidator);
  }

  /**
   * A writable structured field decoded from its complete JSON value by {@code parser}.
   *
   * @param structure JSON Schema keywords, such as {@code items}, published for the value
   */
  public static <V> CollectionFieldType<V> structured(
      Class<V> javaType,
      CollectionFieldType.InputKind inputKind,
      Map<String, Object> structure,
      Function<JsonNode, V> parser,
      Function<V, Object> serializer) {
    if (inputKind == CollectionFieldType.InputKind.ARRAY) {
      throw new IllegalArgumentException("Array fields must declare their children");
    }
    return new StructuredValueFieldType<>(
        Objects.requireNonNull(javaType, "Structured Java type"),
        Objects.requireNonNull(inputKind, "Structured input kind"),
        new CollectionFieldType.Schema(
            inputKind.name().toLowerCase(java.util.Locale.ROOT), null, null, structure),
        Objects.requireNonNull(parser, "Structured parser"),
        Objects.requireNonNull(serializer, "Structured serializer"));
  }

  public static CollectionFieldType<String> text() {
    return text(Integer.MAX_VALUE);
  }

  /** A non-sortable text field with explicit filter operators and wildcard support. */
  public static CollectionFieldType<String> text(Set<Operator> operators, boolean wildcards) {
    return new TextFieldType(operators, wildcards);
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
    return new EnumFieldType<>(type);
  }

  public static CollectionFieldType<String> text(int maxLength) {
    return new TextFieldType(maxLength);
  }

  /**
   * Adds write-only rules to a scalar type. They are checked on writes and defaults and published
   * on body properties only, because {@code where} literals are never checked against them.
   *
   * <p>One decorator holds all of a field's rules, so wrapping a constrained type is rejected, as
   * is an opaque type, which cannot be written at all, and a structured type, whose decoder already
   * validates the complete value and which the parser must still recognise.
   */
  public static <V> CollectionFieldType<V> constrained(
      CollectionFieldType<V> base, Constraints constraints) {
    Objects.requireNonNull(constraints, "Field constraints");
    return constrained(base, constraints.toFieldConstraints());
  }

  public static <V> CollectionFieldType<V> constrained(
      CollectionFieldType<V> base, FieldConstraints constraints) {
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
      FieldConstraints checked =
          new FieldConstraints(minimum, maximum, minLength, pattern, allowed);
      allowed = checked.allowed();
    }

    public FieldConstraints toFieldConstraints() {
      return new FieldConstraints(minimum, maximum, minLength, pattern, allowed);
    }

    public boolean isEmpty() {
      return minimum == null
          && maximum == null
          && minLength == null
          && pattern == null
          && allowed == null;
    }
  }
}
