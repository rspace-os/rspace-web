package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.ApiInventoryBulkOperationRecordResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins the CSV import schemas of the published Inventory OpenAPI spec to the properties the
 * matching Java models serialise, so a property added to a model cannot be left out of the spec
 * again (RSDEV-1580).
 */
class InventoryImportApiSpecTest {

  private static final String SPEC_PATH =
      "src/main/webapp/resources/rspace_api_inventory_specs_2_27_0.yaml";
  private static final String SCHEMAS = "#/components/schemas/";

  private static JsonNode spec;

  @BeforeAll
  static void readSpec() throws IOException {
    spec = new YAMLMapper().readTree(new File(SPEC_PATH));
  }

  static Stream<Arguments> schemaAndModel() {
    return Stream.of(
        Arguments.of("InventoryImportParseResult", new ApiInventoryImportParseResult()),
        Arguments.of("InventoryImportSampleParseResult", new ApiInventoryImportSampleParseResult()),
        Arguments.of(
            "InventoryImportInstrumentParseResult", new ApiInventoryImportInstrumentParseResult()),
        Arguments.of("InventoryImportResult", new ApiInventoryImportResult()),
        Arguments.of("InventoryImportPartialResult", new ApiInventoryImportPartialResult()),
        Arguments.of("InventoryImportPartialResult", new ApiInventoryImportSubSampleImportResult()),
        Arguments.of(
            "InventoryImportSampleImportResult", new ApiInventoryImportSampleImportResult()),
        Arguments.of(
            "InventoryImportInstrumentImportResult",
            new ApiInventoryImportInstrumentImportResult()),
        Arguments.of(
            "InventoryBulkOperationRecordResult", new ApiInventoryBulkOperationRecordResult()));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("schemaAndModel")
  void schemaListsExactlyThePropertiesTheModelSerialises(String schemaName, Object model) {
    Set<String> serialised = new TreeSet<>();
    JsonNode json = new ObjectMapper().valueToTree(model);
    json.fieldNames().forEachRemaining(serialised::add);

    assertEquals(serialised, schemaProperties(spec.at("/components/schemas/" + schemaName)));
  }

  @Test
  void parseFileResponseListsTheThreeParseShapes() {
    List<String> refs = new ArrayList<>();
    spec.at("/paths/~1import~1parseFile/post/responses/200/content/application~1json/schema/anyOf")
        .forEach(shape -> refs.add(shape.path("$ref").asText()));

    assertEquals(
        List.of(
            SCHEMAS + "InventoryImportParseResult",
            SCHEMAS + "InventoryImportSampleParseResult",
            SCHEMAS + "InventoryImportInstrumentParseResult"),
        refs);
  }

  @Test
  void parseFileRequestBodyRequiresTheCsvFilePart() {
    JsonNode body =
        spec.at("/paths/~1import~1parseFile/post/requestBody/content/multipart~1form-data/schema");
    List<String> required = new ArrayList<>();
    body.path("required").forEach(name -> required.add(name.asText()));

    assertEquals(List.of("file"), required);
    assertEquals("binary", body.at("/properties/file/format").asText());
  }

  @Test
  void importFilesResponseIsTheImportResult() {
    assertEquals(
        SCHEMAS + "InventoryImportResult",
        spec.at(
                "/paths/~1import~1importFiles/post/responses/200/content/application~1json/schema/$ref")
            .asText());
  }

  @Test
  void everyRefInTheSpecResolves() {
    List<String> dangling = new ArrayList<>();
    for (JsonNode ref : spec.findValues("$ref")) {
      if (spec.at(ref.asText().substring(1)).isMissingNode()) {
        dangling.add(ref.asText());
      }
    }

    assertEquals(List.of(), dangling);
  }

  /**
   * The property names a schema declares, following its {@code allOf} chain through the document.
   */
  private static Set<String> schemaProperties(JsonNode schema) {
    Set<String> names = new TreeSet<>();
    schema.path("properties").fieldNames().forEachRemaining(names::add);
    for (JsonNode part : schema.path("allOf")) {
      String ref = part.path("$ref").asText();
      names.addAll(schemaProperties(ref.isEmpty() ? part : spec.at(ref.substring(1))));
    }
    return names;
  }
}
