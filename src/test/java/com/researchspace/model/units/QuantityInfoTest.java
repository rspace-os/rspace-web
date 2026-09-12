package com.researchspace.model.units;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class QuantityInfoTest {

  @Test
  void displayStrings() {
    QuantityInfo quantity10L = QuantityInfo.of(BigDecimal.valueOf(10), RSUnitDef.LITRE);
    assertEquals("10 l", quantity10L.toPlainString());

    QuantityInfo quantity10dot0L = QuantityInfo.of(BigDecimal.valueOf(10.0), RSUnitDef.LITRE);
    assertEquals("10 l", quantity10dot0L.toPlainString());

    QuantityInfo quantity5dot1525MG =
        QuantityInfo.of(BigDecimal.valueOf(5.1525), RSUnitDef.MILLI_GRAM);
    assertEquals("5.153 mg", quantity5dot1525MG.toPlainString());

    QuantityInfo quantity5dot1524MG =
        QuantityInfo.of(BigDecimal.valueOf(5.1524), RSUnitDef.MILLI_GRAM);
    assertEquals("5.152 mg", quantity5dot1524MG.toPlainString());
  }

  @Test
  void copyEqualsOriginal() {
    QuantityInfo quantity10L = QuantityInfo.of(BigDecimal.valueOf(10), RSUnitDef.LITRE);
    assertEquals(quantity10L, quantity10L.copy());
  }

  @DisplayName("create 0 quantity for all units")
  @ParameterizedTest
  @EnumSource(RSUnitDef.class)
  void zero(RSUnitDef unitDef) {
    QuantityInfo zero = QuantityInfo.zero(unitDef);
    assertEquals(0, zero.getNumericValue().intValue());
  }

  @Test
  void checkEqualsIgnoringValueScale() {
    QuantityInfo quantity1L = QuantityInfo.of(BigDecimal.valueOf(1), RSUnitDef.LITRE);
    QuantityInfo quantity1dot0L = QuantityInfo.of(BigDecimal.valueOf(1.0), RSUnitDef.LITRE);
    assertTrue(quantity1L.equals(quantity1dot0L));
  }

  @Test
  void canStoreWithoutRoundingMatchesTheStored3dpScale() {
    assertTrue(QuantityInfo.canStoreWithoutRounding(new BigDecimal("0.001")));
    assertTrue(QuantityInfo.canStoreWithoutRounding(new BigDecimal("5")));
    assertTrue(
        QuantityInfo.canStoreWithoutRounding(new BigDecimal("0.5000")),
        "trailing zeros beyond 3dp do not lose information");
    assertTrue(QuantityInfo.canStoreWithoutRounding(new BigDecimal("0.000")));

    assertFalse(
        QuantityInfo.canStoreWithoutRounding(new BigDecimal("0.0005")),
        "would round up to 0.001, storing more than was given");
    assertFalse(
        QuantityInfo.canStoreWithoutRounding(new BigDecimal("0.0004")),
        "would round down to zero, silently discarding the amount");
    assertFalse(QuantityInfo.canStoreWithoutRounding(null));
  }

  @Test
  void canStoreWithoutRoundingRejectsValuesTooLargeForTheColumn() {
    // The column is DECIMAL(19,3), so 16 integer digits is the ceiling. A value above it used to
    // pass this guard (1E+30 has a NEGATIVE scale, which is trivially <= 3) and only failed at the
    // INSERT, turning a bad request into a 500.
    assertTrue(QuantityInfo.canStoreWithoutRounding(new BigDecimal("9999999999999999.999")));
    assertTrue(QuantityInfo.canStoreWithoutRounding(new BigDecimal("1000000000000000")));

    assertFalse(
        QuantityInfo.canStoreWithoutRounding(new BigDecimal("1E+30")),
        "negative scale must not be mistaken for a storable value");
    assertFalse(QuantityInfo.canStoreWithoutRounding(new BigDecimal("10000000000000000")));
    assertFalse(QuantityInfo.canStoreWithoutRounding(new BigDecimal("-1E+30")));
  }
}
