package com.researchspace.model.field.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.EnumUtils;

/**
 * Shows a field only when another field in the same form or template meets a condition.
 *
 * <p>The source is referenced by its field key, which is copied with the field, never by database
 * id. Stored as JSON, for example {@code {"sourceFieldKey":"...","operator":"IS_ANY_OF",
 * "values":["Yes"]}}.
 *
 * <p>Parsing accepts and rejects exactly what the TypeScript {@code parseDisplayRule} does,
 * including on malformed input, and both sides write the same JSON value, so the server and the
 * browser always agree on what a stored rule means. Both use standard JSON libraries (Jackson here,
 * {@code JSON.parse} and {@code JSON.stringify} there).
 *
 * @param sourceFieldKey key of the field the condition reads
 * @param operator how the source's value is compared
 * @param values option values, non-empty, for {@link ConditionOperator#requiresValues()} operators;
 *     empty for {@link ConditionOperator#takesNoValues()} operators; for {@link
 *     ConditionOperator#comparesValue()} operators the one text or number compared, though any
 *     count is accepted here and reported by {@link FieldDisplayRuleValidator}
 */
public record FieldDisplayRule(
    String sourceFieldKey, ConditionOperator operator, List<String> values) {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private static final ObjectWriter WRITER =
      MAPPER
          .writer()
          .with(JsonWriteFeature.ESCAPE_NON_ASCII)
          .without(JsonWriteFeature.WRITE_HEX_UPPER_CASE);

  private static final Set<String> PROPERTIES = Set.of("sourceFieldKey", "operator", "values");

  /**
   * @throws IllegalArgumentException if the source key or operator is missing, a value is null, or
   *     the values do not suit the operator. Whether the key names a real field and the values are
   *     real options is checked by {@link FieldDisplayRuleValidator}.
   */
  public FieldDisplayRule {
    if (sourceFieldKey == null) {
      throw new IllegalArgumentException("A display rule needs a source field key");
    }
    if (operator == null) {
      throw new IllegalArgumentException("A display rule needs an operator");
    }
    if (values != null && values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Display rule values cannot be null");
    }
    values = values == null ? List.of() : List.copyOf(values);
    if (operator.requiresValues() && values.isEmpty()) {
      throw new IllegalArgumentException(operator + " needs at least one value");
    }
    if (operator.takesNoValues() && !values.isEmpty()) {
      throw new IllegalArgumentException(operator + " does not take values");
    }
  }

  /**
   * Parses a stored rule. The input must be a JSON object with only the three known properties; the
   * key and operator must be strings, the operator an exact operator name, and values an array of
   * strings (or absent or null). As with JavaScript's {@code JSON.parse}, a repeated property takes
   * its last value. Agreement with the TypeScript parser holds within Jackson's default stream
   * limits (number length, nesting depth), which only contrived input exceeds.
   *
   * @throws IllegalArgumentException if {@code json} is not a valid rule
   */
  public static FieldDisplayRule fromJson(String json) {
    if (json == null) {
      throw new IllegalArgumentException("Invalid display rule: null");
    }
    JsonNode node;
    try {
      node = MAPPER.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid display rule: " + e.getOriginalMessage(), e);
    }
    return fromJson(node);
  }

  /**
   * Parses a rule that is already a JSON tree, for example a property of a larger request body,
   * with the same checks as {@link #fromJson(String)}.
   *
   * @throws IllegalArgumentException if {@code node} is not a valid rule
   */
  public static FieldDisplayRule fromJson(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException("A display rule must be a JSON object");
    }
    for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
      String name = names.next();
      if (!PROPERTIES.contains(name)) {
        throw new IllegalArgumentException("Unknown display rule property: " + name);
      }
    }
    JsonNode key = node.get("sourceFieldKey");
    if (key == null || !key.isTextual()) {
      throw new IllegalArgumentException("A display rule needs a source field key");
    }
    return new FieldDisplayRule(
        key.textValue(), parseOperator(node.get("operator")), parseValues(node.get("values")));
  }

  private static ConditionOperator parseOperator(JsonNode node) {
    // Exact, case-sensitive name match; Jackson's own enum binding would trim the name.
    ConditionOperator operator =
        node != null && node.isTextual()
            ? EnumUtils.getEnum(ConditionOperator.class, node.textValue())
            : null;
    if (operator == null) {
      throw new IllegalArgumentException("A display rule needs a known operator");
    }
    return operator;
  }

  private static List<String> parseValues(JsonNode node) {
    List<String> values = new ArrayList<>();
    if (node == null || node.isNull()) {
      return values;
    }
    if (!node.isArray()) {
      throw new IllegalArgumentException("Display rule values must be strings");
    }
    for (JsonNode value : node) {
      if (!value.isTextual()) {
        throw new IllegalArgumentException("Display rule values must be strings");
      }
      values.add(value.textValue());
    }
    return values;
  }

  /**
   * The rule as stored JSON, written by Jackson. Its JSON value equals what the TypeScript {@code
   * serializeDisplayRule} writes ({@code values} omitted when empty); the bytes may differ in
   * escaping, which does not matter because rules are always read through a JSON parser. Non-ASCII
   * characters are escaped, so the output is plain ASCII and safe to store even when a value holds
   * a lone UTF-16 surrogate, which a UTF-8 column could not represent.
   */
  public String toJson() {
    ObjectNode json = MAPPER.createObjectNode();
    json.put("sourceFieldKey", sourceFieldKey);
    json.put("operator", operator.name());
    if (!values.isEmpty()) {
      ArrayNode array = json.putArray("values");
      values.forEach(array::add);
    }
    try {
      return WRITER.writeValueAsString(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialise display rule", e);
    }
  }
}
