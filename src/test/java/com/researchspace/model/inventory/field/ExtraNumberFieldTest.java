package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExtraNumberFieldTest {

  @Test
  void acceptsNumbersWithinTheSupportedRange() {
    ExtraNumberField field = new ExtraNumberField();

    field.setData("123.450000000000000000000000000001");
    assertEquals("123.450000000000000000000000000001", field.getData());
  }

  @Test
  void reportsSyntaxAndRangeErrorsWithTheExternalizedMessage() {
    ExtraNumberField field = new ExtraNumberField();

    IllegalArgumentException syntax =
        assertThrows(IllegalArgumentException.class, () -> field.setData("3.14asdf"));
    assertEquals(
        "'3.14asdf' is not a valid number or exceeds the supported precision and range.",
        syntax.getMessage());

    IllegalArgumentException range =
        assertThrows(IllegalArgumentException.class, () -> field.setData("1".repeat(36)));
    assertEquals(
        "'111111111111111111111111111111111111' is not a valid number or exceeds the supported"
            + " precision and range.",
        range.getMessage());
  }
}
