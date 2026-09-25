package com.researchspace.model.units;

import static java.util.stream.Collectors.groupingBy;
import static javax.measure.MetricPrefix.MILLI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.JacksonUtil;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.measure.Quantity;
import javax.measure.quantity.Temperature;
import org.junit.jupiter.api.Test;
import tech.units.indriya.quantity.Quantities;
import tech.units.indriya.unit.Units;

public class RSUnitDefsTest {

  @Test
  public void RSUnitDefs() {
    for (RSUnitDef def : EnumSet.allOf(RSUnitDef.class)) {
      assertNotNull(def.getCategory());
      assertNotNull(def.getLabel());
    }
    assertIdsAreUnique();
  }

  @Test
  public void toJsonIgnoresUnitDefinition() {
    for (RSUnitDef def : EnumSet.allOf(RSUnitDef.class)) {
      String json = JacksonUtil.toJson(def);
      assertThat(json).doesNotContain("definition");
    }
  }

  @Test
  public void fahrenheit() {
    Quantity<Temperature> waterBoilingPointAsC = Quantities.getQuantity(100, Units.CELSIUS);
    assertEquals("212 F", waterBoilingPointAsC.to(RSUnits.FAHRENHEIT).toString());
    assertThat(waterBoilingPointAsC.to(RSUnits.FAHRENHEIT).getValue().doubleValue())
        .isCloseTo(212.0, within(0.001));

    Quantity<Temperature> waterBoilingPointAsF = Quantities.getQuantity(212, RSUnits.FAHRENHEIT);
    assertEquals("100 ℃", waterBoilingPointAsF.to(Units.CELSIUS).toString());
    assertThat(waterBoilingPointAsF.to(Units.KELVIN).getValue().doubleValue())
        .isCloseTo(373.15, within(0.001));
  }

  @Test
  public void allUnitsInCategoryAreComparable() {
    Map<String, List<RSUnitDef>> partionedByCategory =
        EnumSet.allOf(RSUnitDef.class).stream().collect(groupingBy(RSUnitDef::getCategory));
    for (Map.Entry<String, List<RSUnitDef>> entrySet : partionedByCategory.entrySet()) {
      for (RSUnitDef unitDef1 : entrySet.getValue()) {
        for (RSUnitDef unitDef2 : entrySet.getValue()) {
          System.err.println(unitDef1 + " " + unitDef2);
          assertTrue(unitDef1.getDefinition().isCompatible(unitDef2.getDefinition()));
          assertTrue(unitDef2.getDefinition().isCompatible(unitDef1.getDefinition()));
        }
      }
    }
  }

  private void assertIdsAreUnique() {
    assertThat(
            EnumSet.allOf(RSUnitDef.class).stream()
                .map(RSUnitDef::getId)
                .collect(Collectors.toSet()))
        .hasSize(EnumSet.allOf(RSUnitDef.class).size());
  }

  @Test
  public void findRSUnitDefForUnit() {
    assertEquals(RSUnitDef.GRAM, RSUnitDef.getUnitDefByUnit(Units.GRAM).get());
    assertEquals(RSUnitDef.MILLI_LITRE, RSUnitDef.getUnitDefByUnit(MILLI(Units.LITRE)).get());
  }

  @Test
  public void checkUnitsComparability() {
    assertTrue(RSUnitDef.GRAM.isComparable(RSUnitDef.MICRO_GRAM));
    assertFalse(RSUnitDef.GRAM.isComparable(RSUnitDef.MICRO_LITRE));
  }
}
