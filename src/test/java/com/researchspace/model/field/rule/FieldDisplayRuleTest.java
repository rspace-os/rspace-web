package com.researchspace.model.field.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Constructor guards that the shared vectors cannot reach, because they only go through JSON. */
class FieldDisplayRuleTest {

  private static final String KEY = "00000000-0000-4000-8000-000000000001";

  @Test
  void nullValueInListIsRejected() {
    List<String> values = Arrays.asList("a", null);
    assertThrows(
        IllegalArgumentException.class,
        () -> new FieldDisplayRule(KEY, ConditionOperator.IS_ANY_OF, values));
  }

  /** A UTF-8 column cannot store a lone surrogate, so stored rules must be plain ASCII. */
  @Test
  void storedJsonIsAsciiAndRoundTripsNonAsciiValues() {
    List<String> values = List.of("x\uD800y", "😀", "é");
    FieldDisplayRule rule = new FieldDisplayRule(KEY, ConditionOperator.IS_ANY_OF, values);

    String json = rule.toJson();

    assertTrue(json.chars().allMatch(c -> c < 0x80), json);
    assertEquals(rule, FieldDisplayRule.fromJson(json));
  }

  @Test
  void nullValuesMeanNoValues() {
    assertEquals(List.of(), new FieldDisplayRule(KEY, ConditionOperator.HAS_VALUE, null).values());
  }
}
