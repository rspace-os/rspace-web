package com.researchspace.model.field.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

  @Test
  void nullValuesMeanNoValues() {
    assertEquals(List.of(), new FieldDisplayRule(KEY, ConditionOperator.HAS_VALUE, null).values());
  }
}
