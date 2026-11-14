package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.researchspace.api.v2.openapi.*;
import com.researchspace.api.v2.resource.*;
import com.researchspace.featureflags.FeatureFlagResourceOperations;
import com.researchspace.service.*;
import com.researchspace.service.inventory.*;
import java.nio.file.*;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiDocumentExportTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void exportFullDocumentWithoutDatabase() throws Exception {
    try (var context = ApiV2ResourceConfigTest.newContext();
        var handlers = ApiV2TestHandlerMappings.newContext()) {
      context.register(ApiV2ResourceConfig.class, FeatureFlagResourceOperations.class);
      context.refresh();
      var document =
          new ApiV2OpenApiDocumentService(
                  context.getBean(ApiV2OpenApiGenerator.class),
                  context.getBean(ApiV2EndpointCatalog.class),
                  () -> Stream.of(ApiV2TestHandlerMappings.mapping(handlers)))
              .generate();
      var path = Path.of("src/main/webapp/ui/src/modules/common/api/v2/openapi.generated.json");
      assertTagDocumentation(MAPPER.valueToTree(document));
      var json =
          new ObjectMapper()
                  .writerWithDefaultPrettyPrinter()
                  .writeValueAsString(sorted(new ObjectMapper().valueToTree(document)))
              + "\n";
      if (Boolean.getBoolean("updateApiV2OpenApiFixture")) {
        Files.createDirectories(path.getParent());
        Files.writeString(path, json);
      } else {
        assertTrue(Files.exists(path), "OpenAPI missing: run regen");
        assertEquals(json, Files.readString(path), "OpenAPI drift: run regen");
      }
      assertTrue(document.containsKey("paths"));
    }
  }

  /** Keys whose arrays the generator fills from sets, so their order carries no meaning. */
  private static final Set<String> UNORDERED = Set.of("operators", "denialReasonCodes");

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

  private static void assertTagDocumentation(JsonNode document) {
    Set<String> used = new TreeSet<>();
    document
        .path("paths")
        .forEach(
            item ->
                item.forEach(
                    operation -> operation.path("tags").forEach(tag -> used.add(tag.asText()))));
    List<String> declared = new ArrayList<>();
    document
        .path("tags")
        .forEach(
            tag -> {
              declared.add(tag.path("name").asText());
              assertFalse(tag.path("description").asText().isBlank());
              assertFalse(tag.path("description").asText().startsWith("Operations documented by "));
            });
    assertEquals(used, new TreeSet<>(declared));
    assertEquals(used.size(), declared.size());
    assertEquals(declared.stream().sorted().toList(), declared);
    Map<String, Integer> membership = new LinkedHashMap<>();
    List<String> groupNames = new ArrayList<>();
    document
        .path("x-tagGroups")
        .forEach(
            group -> {
              groupNames.add(group.path("name").asText());
              assertFalse(group.path("name").asText().isBlank());
              List<String> names = new ArrayList<>();
              group
                  .path("tags")
                  .forEach(
                      tag -> {
                        names.add(tag.asText());
                        membership.merge(tag.asText(), 1, Integer::sum);
                      });
              assertEquals(names.stream().sorted().toList(), names);
            });
    assertEquals(groupNames.stream().sorted().toList(), groupNames);
    assertEquals(used, membership.keySet());
    membership.values().forEach(count -> assertEquals(1, count));
    assertTrue(used.contains("openapi"));
    assertFalse(used.contains("openapi.json"));
  }
}
