package com.researchspace.model.inventory.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the URI cases shared with the client's check (javaUri.test.ts), so that a change to either
 * side is checked against {@code java.net.URI} as the server uses it.
 */
class InventoryUriFieldCasesTest {

  private static JsonNode loadCases() {
    try (InputStream in =
        InventoryUriFieldCasesTest.class.getResourceAsStream("/inventory/uriFieldCases.json")) {
      return new ObjectMapper().readTree(in).get("cases");
    } catch (IOException e) {
      throw new IllegalStateException("Could not read the URI field cases", e);
    }
  }

  @TestFactory
  Stream<DynamicTest> validateAgreesWithEachCase() {
    JsonNode cases = loadCases();
    assertNotNull(cases, "the shared cases file must have a cases array");
    assertTrue(cases.size() >= 30, "the shared cases must not be empty");
    return StreamSupport.stream(cases.spliterator(), false)
        .map(
            testCase -> {
              String value = testCase.get("value").asText();
              boolean accepted = testCase.get("accepted").asBoolean();
              return DynamicTest.dynamicTest(
                  (accepted ? "accepts " : "rejects ") + '"' + value + '"',
                  () ->
                      assertEquals(
                          accepted,
                          !new InventoryUriField().validate(value).hasErrorMessages(),
                          value));
            });
  }
}
