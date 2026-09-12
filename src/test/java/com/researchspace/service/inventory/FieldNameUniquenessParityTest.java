package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The field-name uniqueness rule is implemented TWICE, and this is the only thing tying the two
 * together.
 *
 * <p>{@code InventoryOperationRequestBuilder.withUniqueFieldNames} decides the names actually
 * stored; the wizard's {@code buildOperationRequest.withUniqueFieldNames} decides the names the
 * confirmation card shows the user before they commit. Each had its own tests and nothing compared
 * them, so changing the suffix format on one side left the preview promising names the server would
 * not store, with both suites green (parallel review, A10).
 *
 * <p>The cases live in a JSON file rather than here so {@code buildOperationRequest.test.ts} can
 * assert the same ones, which it reads through the {@code @testresources} alias. Changing the rule
 * now means changing it on both sides or turning one of the two suites red.
 *
 * <p>Kept apart from {@code InventoryOperationRequestBuilderTest}, which lives in {@code
 * api.v1.controller} to reuse the golden fixtures and so cannot reach this package-private method.
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
          InventoryOperationRequestBuilder.withUniqueFieldNames(fields).stream()
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
