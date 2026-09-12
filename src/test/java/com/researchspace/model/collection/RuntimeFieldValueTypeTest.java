package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeFieldValueTypeTest {
  @Test
  void parsesNumbersWithThePrecisionSupportedByTheSqlPredicate() {
    assertEquals(
        new BigDecimal("0.00000000001"), RuntimeFieldValueType.NUMBER.parse("0.00000000001"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RuntimeFieldValueType.NUMBER.parse("0.0000000000000000000000000000001"));
    assertThrows(
        IllegalArgumentException.class, () -> RuntimeFieldValueType.NUMBER.parse("1".repeat(36)));
    assertThrows(
        IllegalArgumentException.class, () -> RuntimeFieldValueType.NUMBER.parse("1e2147483647"));
  }

  @Test
  void decodesJsonEscapesAndRetainsLegacySingleChoices() {
    assertEquals(
        List.of("line\nbreak", "tab\there", "\"quoted\"", "back\\slash", "α"),
        RuntimeFieldValueType.CHOICE.serialize(
            "[\"line\\nbreak\",\"tab\\there\",\"\\\"quoted\\\"\",\"back\\\\slash\",\"\\u03b1\"]"));
    assertEquals(List.of("legacy"), RuntimeFieldValueType.CHOICE.serialize("legacy"));
    assertEquals(List.of(), RuntimeFieldValueType.CHOICE.serialize("[]"));
    assertThrows(
        IllegalArgumentException.class,
        () -> RuntimeFieldValueType.CHOICE.serialize("[\"invalid\\q\"]"));
  }
}
