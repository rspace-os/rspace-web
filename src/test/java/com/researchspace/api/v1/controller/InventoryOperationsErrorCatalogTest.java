package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the operation endpoint's error catalog (DevDocs/adr/0007): every message code the backend
 * raises must have a catalog entry, or the API would show users a raw code instead of a message.
 *
 * <p>Covers every code the endpoint can raise, not just the {@code errors.inventory.operation.*}
 * block: the validator also raises {@code errors.inventory.quantity.*}, the controller raises a
 * sample code, and the shared API advice raises the cross-cutting {@code api.errors.*} ones.
 */
class InventoryOperationsErrorCatalogTest {

  /**
   * Only {@code server.*.json} files are searched because {@link
   * com.researchspace.service.JsonMessageSource} flattens exactly those with no prefix; every other
   * catalog is namespaced by filename and so cannot be addressed by a bare code.
   */
  private static final Path CATALOG_DIR =
      Path.of("src/main/webapp/ui/src/modules/common/i18n/locales/en-US");

  private static final Path[] SOURCES_RAISING_OPERATION_ERRORS = {
    Path.of(
        "src/main/java/com/researchspace/api/v1/controller/InventoryOperationPostValidator.java"),
    Path.of(
        "src/main/java/com/researchspace/service/inventory/impl/InventoryOperationManagerImpl.java"),
    Path.of(
        "src/main/java/com/researchspace/api/v1/controller/InventoryOperationsApiController.java"),
    Path.of("src/main/java/com/researchspace/api/v1/controller/ApiControllerAdvice.java"),
  };

  /** A dotted code in a string literal: errors.inventory.operation.foo, api.errors.bar. */
  private static final Pattern RAISED_CODE =
      Pattern.compile("\"((?:errors|api)\\.[A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)+)\"");

  @Test
  void everyRaisedErrorCodeHasACatalogEntry() throws IOException {
    Set<String> raised = new HashSet<>();
    for (Path source : SOURCES_RAISING_OPERATION_ERRORS) {
      Matcher matcher = RAISED_CODE.matcher(Files.readString(source));
      while (matcher.find()) {
        raised.add(matcher.group(1));
      }
    }
    assertFalse(raised.isEmpty(), "expected the sources to raise error codes");

    Map<String, String> catalog = loadServerCatalogs();
    Set<String> missing = new java.util.TreeSet<>(raised);
    missing.removeAll(catalog.keySet());
    assertTrue(
        missing.isEmpty(),
        "error codes raised in Java with no entry under " + CATALOG_DIR + ": " + missing);
  }

  /** Flattens every server.*.json exactly as JsonMessageSource does, i.e. with no prefix. */
  private static Map<String, String> loadServerCatalogs() throws IOException {
    Map<String, String> flattened = new TreeMap<>();
    try (Stream<Path> files = Files.list(CATALOG_DIR)) {
      for (Path file :
          files.filter(f -> f.getFileName().toString().startsWith("server.")).toList()) {
        flatten("", new ObjectMapper().readTree(file.toFile()), flattened);
      }
    }
    return flattened;
  }

  private static void flatten(String prefix, JsonNode node, Map<String, String> target) {
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              field ->
                  flatten(
                      prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey(),
                      field.getValue(),
                      target));
    } else if (node.isValueNode()) {
      target.put(prefix, node.asText());
    }
  }
}
