package com.researchspace.model.field.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shows a field only when another field in the same form or template meets a condition.
 *
 * <p>The source is referenced by its field key, which is copied with the field, never by database
 * id. Stored as JSON, for example {@code {"sourceFieldKey":"...","operator":"IS_ANY_OF",
 * "values":["Yes"]}}.
 *
 * <p>Parsing and serialisation match the TypeScript {@code parseDisplayRule} and {@code
 * serializeDisplayRule} exactly, including on malformed input, so the server and the browser always
 * agree on what a stored rule means.
 *
 * @param sourceFieldKey key of the radio or choice field the condition reads
 * @param operator how the source's selected options are compared
 * @param values option values; non-empty for {@link ConditionOperator#requiresValues()} operators
 *     and empty otherwise
 */
public record FieldDisplayRule(
    String sourceFieldKey, ConditionOperator operator, List<String> values) {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private static final Set<String> PROPERTIES = Set.of("sourceFieldKey", "operator", "values");

  /**
   * Blank means empty or only whitespace, where whitespace is exactly JavaScript's {@code \s} set
   * (Java's {@code \s} omits the Unicode spaces), so the TypeScript side agrees on every input.
   */
  private static final Pattern BLANK =
      Pattern.compile(
          "^[ \\t\\n"
              + "\\x0B\\f\\r"
              + "\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]*$");

  /**
   * @throws IllegalArgumentException if the source key is blank, the operator is missing, or the
   *     values do not suit the operator
   */
  public FieldDisplayRule {
    if (sourceFieldKey == null || isBlank(sourceFieldKey)) {
      throw new IllegalArgumentException("A display rule needs a source field key");
    }
    if (operator == null) {
      throw new IllegalArgumentException("A display rule needs an operator");
    }
    if (values != null && values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Display rule values cannot be null");
    }
    values = values == null ? List.of() : List.copyOf(values);
    if (values.stream().anyMatch(FieldDisplayRule::isBlank)) {
      throw new IllegalArgumentException("Display rule values cannot be blank");
    }
    if (operator.requiresValues() && values.isEmpty()) {
      throw new IllegalArgumentException(operator + " needs at least one value");
    }
    if (!operator.requiresValues() && !values.isEmpty()) {
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
    if (node != null && node.isTextual()) {
      for (ConditionOperator operator : ConditionOperator.values()) {
        if (operator.name().equals(node.textValue())) {
          return operator;
        }
      }
    }
    throw new IllegalArgumentException("A display rule needs a known operator");
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

  /** The rule as stored JSON, byte-for-byte what the TypeScript side writes. */
  public String toJson() {
    StringBuilder json = new StringBuilder("{\"sourceFieldKey\":");
    appendJsonString(json, sourceFieldKey);
    json.append(",\"operator\":\"").append(operator.name()).append('"');
    if (!values.isEmpty()) {
      json.append(",\"values\":[");
      for (int i = 0; i < values.size(); i++) {
        if (i > 0) {
          json.append(',');
        }
        appendJsonString(json, values.get(i));
      }
      json.append(']');
    }
    return json.append('}').toString();
  }

  static boolean isBlank(String text) {
    return BLANK.matcher(text).matches();
  }

  /**
   * Quotes a string the way JavaScript's {@code JSON.stringify} does (ECMA-262 QuoteJSONString).
   */
  private static void appendJsonString(StringBuilder json, String text) {
    json.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\b' -> json.append("\\b");
        case '\f' -> json.append("\\f");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        default -> {
          boolean loneHigh =
              Character.isHighSurrogate(c)
                  && (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1)));
          boolean loneLow =
              Character.isLowSurrogate(c)
                  && (i == 0 || !Character.isHighSurrogate(text.charAt(i - 1)));
          if (c < 0x20 || loneHigh || loneLow) {
            json.append(String.format("\\u%04x", (int) c));
          } else {
            json.append(c);
          }
        }
      }
    }
    json.append('"');
  }
}
