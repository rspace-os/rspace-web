package com.researchspace.api.v1.controller;

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
 * Guards the operation endpoint's error catalog (DevDocs/adr/0007): every error code the backend
 * raises must have an entry in the frontend catalog, or the API would show users a raw fallback
 * string.
 */
class InventoryOperationsErrorCatalogTest {

  private static final Path MESSAGE_CATALOG =
      Path.of("src/main/webapp/ui/src/modules/common/i18n/locales/en-US/server.inventory.json");
  private static final Path[] SOURCES_RAISING_OPERATION_ERRORS = {
    Path.of(
        "src/main/java/com/researchspace/api/v1/controller/InventoryOperationPostValidator.java"),
    Path.of(
        "src/main/java/com/researchspace/service/inventory/impl/InventoryOperationManagerImpl.java"),
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
