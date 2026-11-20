package com.researchspace.model.dtos;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A display rule on the wire, for the form field editor and the forms and documents API: show the
 * field when the field whose key is {@code sourceFieldKey} meets the condition.
 *
 * <p>Read from JSON with the rule core's parser ({@link FieldDisplayRule#fromJson(String)}), so the
 * wire accepts exactly the rules that can be stored and rejects the same malformed ones (unknown
 * properties or operators, non-string values, values that do not suit the operator), rather than
 * letting Jackson coerce them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = FieldDisplayRuleDTO.Deserializer.class)
public class FieldDisplayRuleDTO {

  private String sourceFieldKey;

  /** One of {@code IS_ANY_OF}, {@code IS_NONE_OF}, {@code HAS_VALUE}, {@code HAS_NO_VALUE}. */
  private String operator;

  /** Option values; required for IS_ANY_OF and IS_NONE_OF, empty otherwise. */
  private List<String> values = new ArrayList<>();

  /**
   * @return the DTO for {@code rule}, or null if {@code rule} is null
   */
  public static FieldDisplayRuleDTO from(FieldDisplayRule rule) {
    if (rule == null) {
      return null;
    }
    return new FieldDisplayRuleDTO(
        rule.sourceFieldKey(), rule.operator().name(), new ArrayList<>(rule.values()));
  }

  /**
   * Reads a rule from its JSON form, exactly as a stored rule is read.
   *
   * @throws IllegalArgumentException if {@code json} is not a valid rule
   */
  public static FieldDisplayRuleDTO parse(JsonNode json) {
    return from(FieldDisplayRule.fromJson(json));
  }

  /**
   * @throws IllegalArgumentException if the operator is unknown or the rule is incomplete
   */
  public FieldDisplayRule toRule() {
    ConditionOperator parsedOperator = null;
    for (ConditionOperator candidate : ConditionOperator.values()) {
      if (candidate.name().equals(operator)) {
        parsedOperator = candidate;
      }
    }
    if (parsedOperator == null) {
      throw new IllegalArgumentException("Unknown display rule operator: " + operator);
    }
    return new FieldDisplayRule(sourceFieldKey, parsedOperator, values);
  }

  static final class Deserializer extends JsonDeserializer<FieldDisplayRuleDTO> {
    @Override
    public FieldDisplayRuleDTO deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      JsonNode node = parser.readValueAsTree();
      try {
        return parse(node);
      } catch (IllegalArgumentException e) {
        throw JsonMappingException.from(parser, e.getMessage(), e);
      }
    }
  }
}
