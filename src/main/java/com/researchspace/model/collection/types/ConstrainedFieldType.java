package com.researchspace.model.collection.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.FieldComparisonStrategy;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.StructuredFieldType;
import com.researchspace.model.collection.WriteOperation;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Adds write-only rules while retaining the base type's parser and query strategy. */
public record ConstrainedFieldType<V>(CollectionFieldType<V> base, FieldConstraints constraints)
    implements CollectionFieldType<V> {

  public ConstrainedFieldType {
    Objects.requireNonNull(base, "Constrained field type");
    Objects.requireNonNull(constraints, "Field constraints");
    if (base instanceof ConstrainedFieldType<?>
        || base instanceof OpaqueObjectFieldType
        || base instanceof StructuredFieldType<?>) {
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
  }

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
  public boolean accepts(JsonNode value) {
    return base.accepts(value);
  }

  @Override
  public V parseScalar(JsonNode value) {
    return base.parseScalar(value);
  }

  @Override
  public V parseDocument(JsonNode value, WriteOperation operation) {
    return base.parseDocument(value, operation);
  }

  @Override
  public FieldComparisonStrategy comparisonStrategy() {
    return base.comparisonStrategy();
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
