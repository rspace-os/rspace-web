package com.researchspace.model.dtos;

import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A display rule on the wire, for the form field editor and the forms and documents API: show the
 * field when the field whose key is {@code sourceFieldKey} meets the condition.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
}
