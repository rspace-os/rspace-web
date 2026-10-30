package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.api.v2.openapi.ApiV2OpenApiDocumentService;
import com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator;
import com.researchspace.api.v2.openapi.ApiV2TestHandlerMappings;
import com.researchspace.api.v2.resource.ApiV2EndpointCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import io.swagger.v3.core.util.Json31;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the frontend's REST API v2 OpenAPI fixture equal to the document the backend generates, so
 * frontend tests run against the published collection contract instead of a hand-written copy.
 */
class ApiV2OpenApiFixtureTest {

  private static final Path MOCKS = Path.of("src/main/webapp/ui/src/__tests__/mocks");
  private static final String UPDATE_PROPERTY = "updateApiV2OpenApiFixture";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void frontendFixturesMatchTheGeneratedContract() throws IOException {
    Map<Path, String> fixtures = Map.of(MOCKS.resolve("apiV2OpenApi.json"), generatedDocument());
    for (Map.Entry<Path, String> fixture : fixtures.entrySet()) {
      if (Boolean.getBoolean(UPDATE_PROPERTY)) {
        Files.writeString(fixture.getKey(), fixture.getValue());
      } else if (!Files.exists(fixture.getKey())
          || !fixture.getValue().equals(Files.readString(fixture.getKey()))) {
        fail(
            fixture.getKey()
                + " is out of date with the REST API v2 resources. Regenerate it with: mvn test"
                + " -Dfast=true -Dtest=ApiV2OpenApiFixtureTest -D"
                + UPDATE_PROPERTY
                + "=true");
      }
    }
  }

  @Test
  void generatedDocumentIncludesAnnotatedBookingRoutes() throws IOException {
    assertTrue(
        MAPPER.readTree(generatedDocument()).path("paths").has("/api/v2/booking-settings/admin"));
  }

  @Test
  void nestedRelationshipTargetsHaveSparseFieldsets() throws IOException {
    JsonNode parameters =
        MAPPER
            .readTree(generatedDocument())
            .path("paths")
            .path("/api/v2/booking-configurations")
            .path("get")
            .path("parameters");
    for (String name : List.of("fields", "exclude")) {
      JsonNode parameter =
          Stream.of(parameters)
              .flatMap(
                  node -> {
                    List<JsonNode> entries = new ArrayList<>();
                    node.forEach(entries::add);
                    return entries.stream();
                  })
              .filter(node -> name.equals(node.path("name").asText()))
              .findFirst()
              .orElseThrow();
      assertTrue(parameter.path("x-rspace-allowed-fields").has("containers"));
    }
  }

  private static String generatedDocument() throws IOException {
    try (var context = ApiV2ResourceConfigTest.newContext();
        var handlers = ApiV2TestHandlerMappings.newContext()) {
      context.register(ApiV2ResourceConfig.class);
      context.refresh();
      var catalog = context.getBean(ApiV2ResourceCatalog.class);
      JsonNode document =
          Json31.mapper()
              .valueToTree(
                  new ApiV2OpenApiDocumentService(
                          new ApiV2OpenApiGenerator(catalog, "RSpace REST API v2", "2.0.0"),
                          context.getBean(ApiV2EndpointCatalog.class),
                          () -> Stream.of(ApiV2TestHandlerMappings.mapping(handlers)))
                      .generate());
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(document)) + "\n";
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
