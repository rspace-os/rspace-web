package com.researchspace.service.inventory.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.model.record.BaseRecord;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The field-name uniqueness rule is implemented twice, once per language, and the shared cases
 * below are the only thing tying the two together: the same file is asserted from
 * buildOperationRequest.test.ts. Without it, changing the suffix format on one side left the
 * wizard's preview promising names the server would not store, with both suites green.
 */
class OperationFieldNamesTest {

  private static final Path SHARED_CASES =
      Path.of("src/test/resources/inventory/fieldNameUniquenessCases.json");

  static Stream<Arguments> sharedCases() throws IOException {
    JsonNode cases = new ObjectMapper().readTree(SHARED_CASES.toFile()).get("cases");
    List<Arguments> arguments = new ArrayList<>();
    for (JsonNode testCase : cases) {
      List<ApiExtraField> fields = new ArrayList<>();
      for (JsonNode field : testCase.get("fields")) {
        String name = field.get("name").asText();
        fields.add(
            "link".equals(field.get("type").asText())
                ? OperationFieldNames.link(
                    name,
                    "operations.pool.linkFieldName",
                    "HasPart",
                    field.path("targetGlobalId").asText(""))
                : OperationFieldNames.text(name, "operations.passage.numberField", ""));
      }
      List<String> expected = new ArrayList<>();
      testCase.get("expected").forEach(name -> expected.add(name.asText()));
      arguments.add(Arguments.of(testCase.get("description").asText(), fields, expected));
    }
    return arguments.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sharedCases")
  void matchesTheCasesTheTypeScriptImplementationIsHeldTo(
      String description, List<ApiExtraField> fields, List<String> expected) {
    assertEquals(
        expected,
        OperationFieldNames.withUniqueFieldNames(fields).stream()
            .map(ApiExtraField::getName)
            .toList());
  }

  @Test
  void truncatesAComposedNameToLeaveRoomForItsUniquenessSuffix() {
    // A generated name interpolates the origin's own name, which may already be at the column
    // limit; without this the INSERT failed as a 500 after the origins were decremented.
    String atTheLimit = "x".repeat(BaseRecord.DEFAULT_VARCHAR_LENGTH);
    List<ApiExtraField> colliding =
        List.of(
            OperationFieldNames.link(atTheLimit, "operations.pool.linkFieldName", "HasPart", "SS1"),
            OperationFieldNames.link(
                atTheLimit, "operations.pool.linkFieldName", "HasPart", "SS2"));

    List<ApiExtraField> unique = OperationFieldNames.withUniqueFieldNames(colliding);

    assertEquals(
        List.of(BaseRecord.DEFAULT_VARCHAR_LENGTH, BaseRecord.DEFAULT_VARCHAR_LENGTH),
        unique.stream().map(field -> field.getName().length()).toList());
    assertEquals(2, unique.stream().map(ApiExtraField::getName).distinct().count());
  }
}
