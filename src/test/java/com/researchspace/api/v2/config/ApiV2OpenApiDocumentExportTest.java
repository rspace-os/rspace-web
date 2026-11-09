package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.researchspace.api.v2.openapi.*;
import com.researchspace.api.v2.resource.*;
import com.researchspace.service.*;
import com.researchspace.service.inventory.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiDocumentExportTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void exportFullDocumentWithoutDatabase() throws Exception {
    try (var context = ApiV2ResourceConfigTest.newContext();
        var handlers = ApiV2TestHandlerMappings.newContext()) {
      context.register(
          ApiV2ResourceConfig.class,
          com.researchspace.featureflags.FeatureFlagResourceOperations.class);
      context.refresh();
      var document =
          new ApiV2OpenApiDocumentService(
                  new ApiV2OpenApiGenerator(
                      context.getBean(ApiV2ResourceCatalog.class), "RSpace REST API v2", "2.0.0"),
                  context.getBean(ApiV2EndpointCatalog.class),
                  () -> Stream.of(ApiV2TestHandlerMappings.mapping(handlers)))
              .generate();
      var path = Path.of("src/main/webapp/ui/src/modules/common/api/v2/openapi.json");
      var json =
          new com.fasterxml.jackson.databind.ObjectMapper()
                  .writerWithDefaultPrettyPrinter()
                  .writeValueAsString(
                      sorted(
                          new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(document)))
              + "\n";
      if (Boolean.getBoolean("updateApiV2OpenApiFixture")) {
        Files.createDirectories(path.getParent());
        Files.writeString(path, json);
      } else {
        org.junit.jupiter.api.Assertions.assertTrue(
            Files.exists(path), "OpenAPI missing: run regen");
        org.junit.jupiter.api.Assertions.assertEquals(
            json, Files.readString(path), "OpenAPI drift: run regen");
      }
      assertTrue(document.containsKey("paths"));
    }
  }

  /** Keys whose arrays the generator fills from sets, so their order carries no meaning. */
  private static final Set<String> UNORDERED = Set.of("operators", "denialReasonCodes", "tags");

  /**
   * Orders object keys, and the elements of set-valued arrays, so the fixture does not depend on
   * hash iteration order, which differs between JVM runs.
   */
  private static JsonNode sorted(JsonNode node) {
    if (node.isObject()) {
      ObjectNode result = MAPPER.createObjectNode();
      List<String> names = new ArrayList<>();
      node.fieldNames().forEachRemaining(names::add);
      names.stream()
          .sorted()
          .forEach(
              name -> {
                JsonNode value = sorted(node.get(name));
                result.set(name, UNORDERED.contains(name) ? sortedElements(value) : value);
              });
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = MAPPER.createArrayNode();
      node.forEach(element -> result.add(sorted(element)));
      return result;
    }
    return node;
  }

  private static JsonNode sortedElements(JsonNode array) {
    if (!array.isArray()) {
      return array;
    }
    List<JsonNode> elements = new ArrayList<>();
    array.forEach(elements::add);
    elements.sort(Comparator.comparing(JsonNode::toString));
    return MAPPER.createArrayNode().addAll(elements);
  }
}
