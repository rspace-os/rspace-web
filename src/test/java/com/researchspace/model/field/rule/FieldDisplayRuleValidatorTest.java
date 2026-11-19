package com.researchspace.model.field.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.researchspace.model.field.FieldType;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldDisplayRuleValidatorTest {

  private static final String SOURCE_KEY = "00000000-0000-4000-8000-000000000001";
  private static final String TARGET_KEY = "00000000-0000-4000-8000-000000000002";

  /** Serialising escapes each non-ASCII unit to six characters, so huge rules must not reach it. */
  @Test
  void valuesTogetherOverTheBudgetAreTooLongWithoutSerialising() {
    List<String> values = List.of("é".repeat(20_000), "è".repeat(20_000));
    FieldDisplayRule rule =
        spy(new FieldDisplayRule(SOURCE_KEY, ConditionOperator.IS_ANY_OF, values));
    List<FieldSnapshot> fields =
        List.of(
            new FieldSnapshot(SOURCE_KEY, FieldType.RADIO, null, values, List.of()),
            new FieldSnapshot(TARGET_KEY, FieldType.STRING, rule, List.of(), List.of()));

    assertEquals(
        List.of(
            new FieldDisplayRuleValidator.RuleError(1, FieldDisplayRuleValidator.RULE_TOO_LONG)),
        FieldDisplayRuleValidator.validate(fields));
    verify(rule, never()).toJson();
  }
}
