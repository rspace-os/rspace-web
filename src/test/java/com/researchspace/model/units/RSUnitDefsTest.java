package com.researchspace.model.units;

import static java.util.stream.Collectors.groupingBy;
import static javax.measure.MetricPrefix.MILLI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.researchspace.api.v1.controller.UnitsApiController;
import com.researchspace.core.util.JacksonUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.measure.Quantity;
import javax.measure.quantity.Temperature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.units.indriya.quantity.Quantities;
import tech.units.indriya.unit.Units;

public class RSUnitDefsTest {

  /**
   * The frontend hardcodes the unit list; its ids, categories and order must match what GET
   * /api/v1/units returns. Labels are the frontend's own, so they are not compared.
   */
  @Test
  public void frontendUnitTableMatchesApi() throws IOException {
    List<Map<String, Object>> frontend =
        new ObjectMapper()
                .readValue(
                    Path.of("src/main/webapp/ui/src/stores/definitions/units.json").toFile(),
                    new TypeReference<List<Map<String, Object>>>() {})
                .stream()
                .map(u -> Map.of("id", u.get("id"), "category", u.get("category")))
                .toList();
    List<Map<String, Object>> api =
        UnitsApiController.SORTED_UNITS.stream()
            .map(u -> Map.<String, Object>of("id", u.getId(), "category", u.getCategory()))
            .toList();
    assertEquals(api, frontend);
  }

  private static final String UNIT_ID_SCHEMA = "/components/schemas/UnitId";

  private static final Pattern UNIT_TABLE_ROW =
      Pattern.compile("^\\| (\\d+) \\| `(\\w+)` \\|.*\\| (\\w+) \\|$", Pattern.MULTILINE);

  /**
   * Each OpenAPI spec defines the unit ids as the {@code UnitId} enum, named after this enum's
   * constants, with a table giving each unit's category.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "src/main/webapp/resources/rspace_api_specs_2_28_0.yaml",
        "src/main/webapp/resources/rspace_api_inventory_specs_2_28_0.yaml"
      })
  public void openApiUnitIdMatchesEnum(String specPath) throws IOException {
    JsonNode unitId = new YAMLMapper().readTree(Path.of(specPath).toFile()).at(UNIT_ID_SCHEMA);
    List<RSUnitDef> byId =
        Arrays.stream(RSUnitDef.values()).sorted(Comparator.comparing(RSUnitDef::getId)).toList();

    assertEquals(byId.stream().map(RSUnitDef::getId).toList(), intsOf(unitId.get("enum")));
    assertEquals(
        byId.stream().map(RSUnitDef::name).toList(), textsOf(unitId.get("x-enum-varnames")));

    Matcher row = UNIT_TABLE_ROW.matcher(unitId.get("description").asText());
    List<List<Object>> table = new ArrayList<>();
    while (row.find()) {
      table.add(List.of(Integer.valueOf(row.group(1)), row.group(2), row.group(3)));
    }
    assertEquals(
        byId.stream().map(u -> List.<Object>of(u.getId(), u.name(), u.getCategory())).toList(),
        table);
  }

  /** The documented GET /units response must be what the endpoint serialises. */
  @Test
  public void openApiUnitsExampleMatchesApi() throws IOException {
    JsonNode spec =
        new YAMLMapper()
            .readTree(Path.of("src/main/webapp/resources/rspace_api_specs_2_28_0.yaml").toFile());
    JsonNode example =
        spec.at(
            "/paths/~1units/get/responses/200/content/application~1json/examples/response/value");
    assertThat(example.size()).isPositive();
    ObjectMapper json = new ObjectMapper();
    elementsOf(example)
        .forEach(
            unit ->
                assertEquals(
                    json.valueToTree(RSUnitDef.getUnitById(unit.get("id").asInt())), unit));
    assertEquals(
        EnumSet.allOf(RSUnitDef.class).stream()
            .map(RSUnitDef::getCategory)
            .distinct()
            .sorted()
            .toList(),
        textsOf(spec.at("/components/schemas/Unit/properties/category/enum")));
  }

  private static Stream<JsonNode> elementsOf(JsonNode array) {
    return StreamSupport.stream(array.spliterator(), false);
  }

  private static List<Integer> intsOf(JsonNode array) {
    return elementsOf(array).map(JsonNode::asInt).toList();
  }

  private static List<String> textsOf(JsonNode array) {
    return elementsOf(array).map(JsonNode::asText).toList();
  }

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
