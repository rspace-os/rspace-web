package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.ApiInventoryBulkOperationRecordResult;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the CSV import schemas of the published Inventory OpenAPI spec to the properties the
 * matching Java models serialise, so a property added to a model cannot be left out of the spec
 * again (RSDEV-1580).
 */
class InventoryImportApiSpecTest {

  private static final String SPEC_PATH =
      "src/main/webapp/resources/rspace_api_inventory_specs_2_28_0.yaml";
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
   * In OpenAPI 3.0 {@code nullable} applies only beside {@code type}, and each {@code allOf} member
   * is validated on its own, so a member that declares a type rejects null.
   */
  @Test
  void nullableReferencesAcceptNull() {
    List<String> rejectingNull = new ArrayList<>();
    findNullableReferencesRejectingNull(spec, "", rejectingNull);

    assertEquals(List.of(), rejectingNull);
  }

  /**
   * Scalar overflows its stack rendering a schema that refers back to itself, as Container does.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "InventoryImportParseResult",
        "InventoryImportSampleParseResult",
        "InventoryImportInstrumentParseResult",
        "InventoryImportResult",
        "InventoryImportPartialResult",
        "InventoryImportSampleImportResult",
        "InventoryImportInstrumentImportResult",
        "InventoryBulkOperationRecordResult"
      })
  void importSchemaReachesNoCycle(String schemaName) {
    assertEquals(List.of(), cycleFrom(SCHEMAS + schemaName, new ArrayList<>(), new HashSet<>()));
  }

  static Stream<Arguments> enumAndJavaEnum() {
    return Stream.of(
        Arguments.of("InventoryRecordInfo/properties/type", ApiInventoryRecordType.class),
        Arguments.of("InventoryImportPartialResult/properties/type", ApiInventoryRecordType.class),
        Arguments.of(
            "InventoryImportPartialResult/properties/status", InventoryBulkOperationStatus.class),
        Arguments.of("InventoryImportResult/properties/status", InventoryBulkOperationStatus.class),
        Arguments.of("InventoryEntityField/properties/type", ApiFieldType.class));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("enumAndJavaEnum")
  void enumListsTheJavaEnumValues(String property, Class<? extends Enum<?>> javaEnum) {
    Set<String> declared = new TreeSet<>();
    spec.at("/components/schemas/" + property + "/enum").forEach(v -> declared.add(v.asText()));

    assertEquals(
        Arrays.stream(javaEnum.getEnumConstants())
            .map(value -> new ObjectMapper().convertValue(value, String.class))
            .collect(Collectors.toCollection(TreeSet::new)),
        declared);
  }

  private static void findNullableReferencesRejectingNull(
      JsonNode node, String path, List<String> found) {
    if (node.path("nullable").asBoolean() && !node.has("type")) {
      for (JsonNode member : node.path("allOf")) {
        if (declaresTypeWithoutNullable(member)) {
          found.add(path);
          break;
        }
      }
    }
    if (node.isArray()) {
      for (int i = 0; i < node.size(); i++) {
        findNullableReferencesRejectingNull(node.get(i), path + "/" + i, found);
      }
    } else {
      node.fieldNames()
          .forEachRemaining(
              name ->
                  findNullableReferencesRejectingNull(node.get(name), path + "/" + name, found));
    }
  }

  private static List<String> cycleFrom(String ref, List<String> path, Set<String> acyclic) {
    if (path.contains(ref)) {
      List<String> cycle = new ArrayList<>(path.subList(path.indexOf(ref), path.size()));
      cycle.add(ref);
      return cycle;
    }
    if (acyclic.contains(ref)) {
      return List.of();
    }
    path.add(ref);
    for (JsonNode next : spec.at(ref.substring(1)).findValues("$ref")) {
      List<String> cycle = cycleFrom(next.asText(), path, acyclic);
      if (!cycle.isEmpty()) {
        return cycle;
      }
    }
    path.remove(path.size() - 1);
    acyclic.add(ref);
    return List.of();
  }

  private static boolean declaresTypeWithoutNullable(JsonNode schema) {
    String ref = schema.path("$ref").asText();
    JsonNode resolved = ref.isEmpty() ? schema : spec.at(ref.substring(1));
    if (resolved.has("type") && !resolved.path("nullable").asBoolean()) {
      return true;
    }
    for (JsonNode member : resolved.path("allOf")) {
      if (declaresTypeWithoutNullable(member)) {
        return true;
      }
    }
    return false;
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
