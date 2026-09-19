package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.service.inventory.operations.OperationFieldNames;
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
 * Guards the operation endpoint's error catalog: every message code the backend raises must have a
 * catalog entry, or the API would show users a raw code instead of a message.
 *
 * <p>Covers every code the endpoint can raise, not just the {@code errors.inventory.operation.*}
 * block: the validator also raises {@code errors.inventory.quantity.*}, the controller raises a
 * sample code, and the shared API advice raises the cross-cutting {@code api.errors.*} ones.
 */
class InventoryOperationsErrorCatalogTest {

  private static final Path CATALOG_DIR =
      Path.of("src/main/webapp/ui/src/modules/common/i18n/locales/en-US");

  private static final Path CONTROLLER_DIR =
      Path.of("src/main/java/com/researchspace/api/v1/controller");

  /** Every operation class and shared rule holder; each raises codes of its own. */
  private static final Path OPERATIONS_DIR =
      Path.of("src/main/java/com/researchspace/service/inventory/operations");

  /** The request bodies, whose annotations name catalog keys the same way Java code does. */
  private static final Path REQUESTS =
      Path.of("src/main/java/com/researchspace/api/v1/model/ApiInventoryOperationRequests.java");

  /**
   * Every file in the controller package whose name starts with Operation or InventoryOperation is
   * matched, rather than named one by one, so splitting a validator cannot quietly take its codes
   * out of scope. The whole operations package is scanned for the same reason, and the two files
   * outside both packages are named explicitly.
   */
  private static Path[] sourcesRaisingOperationErrors() throws IOException {
    List<Path> sources = new ArrayList<>();
    try (Stream<Path> files = Files.list(OPERATIONS_DIR)) {
      files.filter(file -> file.getFileName().toString().endsWith(".java")).forEach(sources::add);
    }
    sources.add(REQUESTS);
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
   * codes are added; an unexplained DROP means a source file stopped being scanned rather than that
   * rules were removed, which is the failure this floor exists to catch.
   */
  private static final int MINIMUM_CODES_RAISED = 39;

  /**
   * A dotted code in a string literal: errors.inventory.operation.foo, api.errors.bar. The optional
   * braces are the bean-validation message template form, {@code
   * "{errors.inventory.operation.foo}"}, which the request bodies use in their annotations; without
   * them those keys were scanned and silently matched nothing.
   */
  private static final Pattern RAISED_CODE =
      Pattern.compile("\"\\{?((?:errors|api)\\.[A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)+)\\}?\"");

  /** A field-label key in a string literal: operations.passage.numberField. */
  private static final Pattern LABEL_KEY =
      Pattern.compile("\"(operations\\.[A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)*)\"");

  /** As above: a DROP means a file stopped being scanned, not that labels were removed. */
  private static final int MINIMUM_LABELS_REFERENCED = 10;

  @Test
  void everyRaisedErrorCodeHasACatalogEntry() throws IOException {
    Set<String> raised = new HashSet<>();
    for (Path source : sourcesRaisingOperationErrors()) {
      Matcher matcher = RAISED_CODE.matcher(Files.readString(source));
      while (matcher.find()) {
        raised.add(matcher.group(1));
      }
    }
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

  /**
   * The names an operation gives the fields it generates. Unlike an error code, a key that misses
   * the catalog is not a bad message on screen: {@link
   * com.researchspace.service.inventory.operations.LabelResolver} falls back to the key itself, and
   * {@code CreatingOperation} stores that as the created sample's extra-field name, so the string
   * {@code operations.passage.numberField} is persisted and outlives the typo.
   */
  @Test
  void everyOperationFieldLabelHasACatalogEntry() throws IOException {
    Set<String> referenced = new HashSet<>();
    try (Stream<Path> files = Files.list(OPERATIONS_DIR)) {
      for (Path source : files.filter(f -> f.toString().endsWith(".java")).toList()) {
        Matcher matcher = LABEL_KEY.matcher(Files.readString(source));
        while (matcher.find()) {
          referenced.add(matcher.group(1));
        }
      }
    }
    // Not a label: it is only ever an operationFieldKey, the stable identifier a later operation
    // matches on. The documentation link's visible name is operations.documentation.fieldName.
    referenced.remove(OperationFieldNames.DOCUMENTATION_LINK_KEY);
    assertTrue(
        referenced.size() >= MINIMUM_LABELS_REFERENCED,
        () -> "only " + referenced.size() + " label keys scanned under " + OPERATIONS_DIR);

    Map<String, String> catalog = new TreeMap<>();
    flatten(
        "", new ObjectMapper().readTree(CATALOG_DIR.resolve("inventory.json").toFile()), catalog);
    Set<String> missing = new java.util.TreeSet<>(referenced);
    missing.removeAll(catalog.keySet());
    assertTrue(
        missing.isEmpty(), "operation field labels with no entry in inventory.json: " + missing);
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
