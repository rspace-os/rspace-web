package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

  private static final Path CONTROLLER_DIR =
      Path.of("src/main/java/com/researchspace/api/v1/controller");

  /**
   * Every file in the controller package whose name starts with Operation or InventoryOperation is
   * matched, rather than named one by one, so splitting a validator cannot quietly take its codes
   * out of scope: that is exactly what happened when the 994-line validator became four classes
   * (parallel review). The two files outside that package are named explicitly.
   */
  private static Path[] sourcesRaisingOperationErrors() throws IOException {
    List<Path> sources = new ArrayList<>();
    try (Stream<Path> files = Files.list(CONTROLLER_DIR)) {
      files
          .filter(
              file -> {
                String name = file.getFileName().toString();
                return name.endsWith(".java")
                    && (name.startsWith("Operation") || name.startsWith("InventoryOperation"));
              })
          .forEach(sources::add);
    }
    sources.add(
        Path.of(
            "src/main/java/com/researchspace/service/inventory/impl/InventoryOperationManagerImpl.java"));
    sources.add(CONTROLLER_DIR.resolve("ApiControllerAdvice.java"));
    return sources.toArray(new Path[0]);
  }

  /**
   * The endpoint raised this many distinct codes when the guard was last reviewed. Raise it when
   * codes are added; a DROP means a source file stopped being scanned rather than that rules were
   * removed, which is the failure this floor exists to catch.
   */
  private static final int MINIMUM_CODES_RAISED = 40;

  /** A dotted code in a string literal: errors.inventory.operation.foo, api.errors.bar. */
  private static final Pattern RAISED_CODE =
      Pattern.compile("\"((?:errors|api)\\.[A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)+)\"");

  @Test
  void everyRaisedErrorCodeHasACatalogEntry() throws IOException {
    Set<String> raised = new HashSet<>();
    for (Path source : sourcesRaisingOperationErrors()) {
      Matcher matcher = RAISED_CODE.matcher(Files.readString(source));
      while (matcher.find()) {
        raised.add(matcher.group(1));
      }
    }
    // A floor, not just non-empty. The G1 split moved 30-odd codes out of the validator into three
    // new files, and because the source list was not updated with them this guard went on passing
    // while scanning almost nothing (parallel review). "Not empty" is satisfied by a single file,
    // so
    // it cannot detect that narrowing; a count can.
    assertTrue(
        raised.size() >= MINIMUM_CODES_RAISED,
        () ->
            "only "
                + raised.size()
                + " codes scanned, expected at least "
                + MINIMUM_CODES_RAISED
                + ". Did a refactor move error codes into a file not listed in"
                + " the scanned set? Raised: "
                + new java.util.TreeSet<>(raised));

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
