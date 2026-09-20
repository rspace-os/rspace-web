package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.service.inventory.operations.OperationFieldNames;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The field-name uniqueness rule is implemented TWICE, and this is the only thing tying the two
 * together: {@code OperationFieldNames.withUniqueFieldNames} decides the names actually stored, and
 * the wizard's {@code buildOperationRequest.withUniqueFieldNames} decides the names the
 * confirmation card shows the user before they commit.
 *
 * <p>The cases live in a JSON file rather than here so {@code buildOperationRequest.test.ts} can
 * assert the same ones. Changing the rule means changing it on both sides or turning one of the two
 * suites red.
 */
class FieldNameUniquenessParityTest {

  private static final Path CASES =
      Path.of("src/test/resources/inventory/fieldNameUniquenessCases.json");

  @Test
  void matchesTheWizardOnEverySharedCase() throws Exception {
    JsonNode cases = new ObjectMapper().readTree(CASES.toFile()).get("cases");
    assertTrue(
        cases != null && cases.size() >= 5,
        "the shared fixture must carry real cases, or this test passes vacuously while the two"
            + " implementations drift");

    for (JsonNode testCase : cases) {
      List<ApiExtraField> fields = new ArrayList<>();
      for (JsonNode field : testCase.get("fields")) {
        fields.add(
            "link".equals(field.get("type").asText())
                ? linkNamed(field.get("name").asText(), field.get("targetGlobalId").asText())
                : textNamed(field.get("name").asText()));
      }

      List<String> actual =
          OperationFieldNames.withUniqueFieldNames(fields).stream()
              .map(ApiExtraField::getName)
              .toList();

      List<String> expected = new ArrayList<>();
      testCase.get("expected").forEach(name -> expected.add(name.asText()));
      assertEquals(expected, actual, testCase.get("description").asText());
    }
  }

  private static ApiExtraField linkNamed(String name, String targetGlobalId) {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.LINK);
    field.setName(name);
    ApiInventoryLink link = new ApiInventoryLink();
    link.setTargetGlobalId(targetGlobalId);
    field.setLink(link);
    return field;
  }

  private static ApiExtraField textNamed(String name) {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
    field.setName(name);
    return field;
  }
}
