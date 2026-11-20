package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldDisplayRuleConverterTest {

  private final FieldDisplayRuleConverter converter = new FieldDisplayRuleConverter();

  @Test
  void ruleRoundTripsThroughItsColumnText() {
    FieldDisplayRule rule =
        new FieldDisplayRule(
            "6f1c2e0a-8d5b-4c51-9a3e-2b7f1d0c4e9a", ConditionOperator.IS_NONE_OF, List.of("No"));

    assertEquals(rule, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(rule)));
    assertNull(converter.convertToDatabaseColumn(null));
    assertNull(converter.convertToEntityAttribute(null));
  }

  @Test
  void unreadableStoredRuleLoadsAsNoRule() {
    assertNull(converter.convertToEntityAttribute("{not json"));
    assertNull(converter.convertToEntityAttribute("{\"sourceFieldKey\":\"x\"}"));
    assertNull(converter.convertToEntityAttribute("[]"));
  }
}
