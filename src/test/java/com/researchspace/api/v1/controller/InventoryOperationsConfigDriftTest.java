package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the stage-1 contract of DevDocs/adr/0015: the backend's copy of the operation definitions
 * is a VERBATIM copy of the frontend's. The frontend renders and gates the wizard from its copy;
 * the backend validates API requests against this one. If they drift, the two sides disagree about
 * what a valid operation is, so drift is a red build, not a warning.
 */
class InventoryOperationsConfigDriftTest {

  private static final Path FRONTEND_CONFIG =
      Path.of("src/main/webapp/ui/src/Inventory/components/Operations/operations_config.json");
  private static final Path BACKEND_CONFIG =
      Path.of("src/main/resources/inventory/operations_config.json");

  @Test
  void backendConfigIsAByteIdenticalCopyOfTheFrontendConfig() throws IOException {
    assertTrue(Files.exists(FRONTEND_CONFIG), "frontend config missing: " + FRONTEND_CONFIG);
    assertTrue(Files.exists(BACKEND_CONFIG), "backend config missing: " + BACKEND_CONFIG);
    assertArrayEquals(
        Files.readAllBytes(FRONTEND_CONFIG),
        Files.readAllBytes(BACKEND_CONFIG),
        "The two operations_config.json copies have drifted. They must stay byte-identical"
            + " (DevDocs/adr/0015): after editing one, sync the other with\n  cp "
            + FRONTEND_CONFIG
            + " "
            + BACKEND_CONFIG
            + "\n(or the reverse direction if the backend copy was edited).");
  }

  private static final Path MESSAGE_CATALOG =
      Path.of("src/main/webapp/ui/src/modules/common/i18n/locales/en-US/server.inventory.json");
  private static final Path[] SOURCES_RAISING_OPERATION_ERRORS = {
    Path.of(
        "src/main/java/com/researchspace/api/v1/controller/InventoryOperationPostValidator.java"),
    Path.of(
        "src/main/java/com/researchspace/api/v1/controller/InventoryOperationsApiController.java"),
  };

  /**
   * Same idea as the config drift test, for the error messages: every {@code
   * errors.inventory.operation.*} code the backend raises must have a catalog entry, or the API
   * would return a raw fallback string to users.
   */
  @Test
  void everyRaisedOperationErrorCodeHasACatalogEntry() throws IOException {
    Set<String> raised = new HashSet<>();
    Pattern code = Pattern.compile("errors\\.inventory\\.operation\\.([A-Za-z]+)");
    for (Path source : SOURCES_RAISING_OPERATION_ERRORS) {
      Matcher matcher = code.matcher(Files.readString(source));
      while (matcher.find()) {
        raised.add(matcher.group(1));
      }
    }
    assertFalse(raised.isEmpty(), "expected the sources to raise operation error codes");

    JsonNode catalogOperationBlock =
        new ObjectMapper()
            .readTree(MESSAGE_CATALOG.toFile())
            .path("errors")
            .path("inventory")
            .path("operation");
    Set<String> missing = new HashSet<>();
    for (String key : raised) {
      if (!catalogOperationBlock.has(key)) {
        missing.add(key);
      }
    }
    assertTrue(
        missing.isEmpty(),
        "error codes raised in Java without a catalog entry in "
            + MESSAGE_CATALOG
            + ": "
            + missing);
  }
}
