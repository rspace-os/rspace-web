package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class RuntimeFieldValueTypeTest {
  @ParameterizedTest
  @EnumSource(RuntimeFieldValueType.class)
  void preservesSharedFilterLimitsAndAbsentStoredValues(RuntimeFieldValueType type) {
    assertThrows(IllegalArgumentException.class, () -> type.parse(null));
    assertThrows(IllegalArgumentException.class, () -> type.parse("x".repeat(1001)));
    assertNull(type.serialize(null));
    assertNull(type.serialize(""));
  }

  @Test
  void validatesIsoFilterValuesButPreservesStoredText() {
    assertEquals("2024-02-29", RuntimeFieldValueType.DATE.parse("2024-02-29"));
    assertEquals("23:59", RuntimeFieldValueType.TIME.parse("23:59"));
    assertThrows(
        IllegalArgumentException.class, () -> RuntimeFieldValueType.DATE.parse("2023-02-29"));
    assertThrows(IllegalArgumentException.class, () -> RuntimeFieldValueType.TIME.parse("24:00"));
    assertEquals("legacy date", RuntimeFieldValueType.DATE.serialize("legacy date"));
    assertEquals("legacy time", RuntimeFieldValueType.TIME.serialize("legacy time"));
  }

  @Test
  void parsesNumbersWithThePrecisionSupportedByTheSqlPredicate() {
    assertEquals(new BigDecimal("1e-30"), RuntimeFieldValueType.NUMBER.parse("1e-30"));
    assertEquals(
        new BigDecimal("0.00000000001"), RuntimeFieldValueType.NUMBER.parse("0.00000000001"));
    assertEquals(
        new BigDecimal("1.0000000000000000000000000000000"),
        RuntimeFieldValueType.NUMBER.parse("1.0000000000000000000000000000000"));
    assertThrows(IllegalArgumentException.class, () -> RuntimeFieldValueType.NUMBER.parse("1e-31"));
    assertThrows(IllegalArgumentException.class, () -> RuntimeFieldValueType.NUMBER.parse("1\n"));
    assertThrows(
        IllegalArgumentException.class, () -> RuntimeFieldValueType.NUMBER.parse("\u0661\u0662"));
    assertNull(RuntimeFieldValueType.NUMBER.serialize("\u0661\u0662"));
    assertThrows(
        IllegalArgumentException.class, () -> RuntimeFieldValueType.NUMBER.parse("1".repeat(36)));
    assertThrows(
        IllegalArgumentException.class, () -> RuntimeFieldValueType.NUMBER.parse("1e2147483647"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"0", "-0", "+12", "12.", ".5", "-.5", "1.25", "+1.25e-3", "12.e+2", ".5E+1"})
  void preservesNumericSyntax(String value) {
    assertEquals(new BigDecimal(value), RuntimeFieldValueType.parseNumber(value));
  }

  @Test
  void rejectsLongMalformedStoredNumbersWithoutExcessiveBacktracking() {
    String stored = "0".repeat(20_000) + "x";

    assertTimeout(
        Duration.ofSeconds(1), () -> assertNull(RuntimeFieldValueType.NUMBER.serialize(stored)));
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
