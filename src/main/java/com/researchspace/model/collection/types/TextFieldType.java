package com.researchspace.model.collection.types;

import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.Operator;
import java.util.Objects;
import java.util.Set;

/** Text literals with parse-time length and configurable query capabilities. */
public record TextFieldType(
    Integer maxLength, Set<Operator> operators, boolean supportsWildcards, boolean sortable)
    implements CollectionFieldType<String> {
  public TextFieldType(int maxLength) {
    this(maxLength, TypeOperators.TEXT, true, true);
  }

  public TextFieldType(Set<Operator> operators, boolean wildcards) {
    this(null, operators, wildcards, false);
  }

  public TextFieldType {
    if (maxLength != null && maxLength < 0) {
      throw new IllegalArgumentException("Text maximum length must not be negative");
    }
    operators = Set.copyOf(Objects.requireNonNull(operators, "Text operators"));
  }

  @Override
  public Class<String> javaType() {
    return String.class;
  }

  @Override
  public InputKind inputKind() {
    return InputKind.STRING;
  }

  @Override
  public Schema schema() {
    return new Schema(
        "string", null, maxLength == null || maxLength == Integer.MAX_VALUE ? null : maxLength);
  }

  @Override
  public String parse(String value) {
    validate(value);
    return value;
  }

  @Override
  public void validate(String value) {
    if (maxLength != null && value.length() > maxLength) {
      throw new IllegalArgumentException("Text value exceeds its maximum length");
    }
  }

  @Override
  public Object serialize(String value) {
    return value;
  }

  @Override
  public FieldComparisonStrategy comparisonStrategy() {
    return operators.isEmpty() ? FieldComparisonStrategy.NONE : FieldComparisonStrategy.SCALAR;
  }
}
