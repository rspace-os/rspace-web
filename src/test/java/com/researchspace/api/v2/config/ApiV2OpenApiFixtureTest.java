package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.*;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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

  @Test
  void documentsCalendarSubscriptionManagementWithTheCurrentUrl() throws IOException {
    Map<String, Object> document = objectMap(MAPPER.readValue(generatedDocument(), Map.class));
    Map<String, Object> paths = objectMap(document.get("paths"));
    Map<String, Object> management =
        objectMap(
            paths.get("/api/v2/booking-configurations/{configurationId}/calendar-subscription"));

    assertEquals(Set.of("get", "post", "delete"), management.keySet());
    assertEquals(
        "getBookingCalendarSubscription", objectMap(management.get("get")).get("operationId"));
    assertEquals(
        "createOrReplaceBookingCalendarSubscription",
        objectMap(management.get("post")).get("operationId"));
    assertEquals(
        "revokeBookingCalendarSubscription",
        objectMap(management.get("delete")).get("operationId"));
    Map<String, Object> get = objectMap(management.get("get"));
    Map<String, Object> post = objectMap(management.get("post"));
    Map<String, Object> delete = objectMap(management.get("delete"));
    assertEquals(
        Set.of("200", "401", "403", "404", "406", "429", "500"),
        objectMap(get.get("responses")).keySet());
    assertEquals(
        Set.of("200", "400", "401", "403", "404", "406", "409", "428", "429", "500"),
        objectMap(post.get("responses")).keySet());
    assertEquals(
        Set.of("204", "401", "403", "404", "406", "429", "500"),
        objectMap(delete.get("responses")).keySet());

    Map<String, Object> ifMatch =
        objectMapList(post.get("parameters")).stream()
            .filter(parameter -> parameter.get("name").equals("If-Match"))
            .findFirst()
            .orElseThrow();
    assertEquals("header", ifMatch.get("in"));
    assertEquals(true, ifMatch.get("required"));
    assertEquals("string", objectMap(ifMatch.get("schema")).get("type"));

    for (Map<String, Object> operation : List.of(get, post)) {
      Map<String, Object> headers =
          objectMap(objectMap(objectMap(operation.get("responses")).get("200")).get("headers"));
      assertEquals(Set.of("Cache-Control", "ETag"), headers.keySet());
      Map<String, Object> cacheControlSchema =
          objectMap(objectMap(headers.get("Cache-Control")).get("schema"));
      assertEquals(List.of("private, no-store"), cacheControlSchema.get("enum"));
      assertEquals("#/components/headers/ETag", objectMap(headers.get("ETag")).get("$ref"));
    }
    assertEquals(
        Set.of("Cache-Control"),
        objectMap(objectMap(objectMap(delete.get("responses")).get("204")).get("headers"))
            .keySet());

    Map<String, Object> etagHeader =
        objectMap(objectMap(objectMap(document.get("components")).get("headers")).get("ETag"));
    String etagPattern = String.valueOf(objectMap(etagHeader.get("schema")).get("pattern"));
    Pattern strongEtag = Pattern.compile(etagPattern);
    assertTrue(strongEtag.matcher("\"inactive\"").matches());
    assertTrue(strongEtag.matcher("\"" + "a".repeat(64) + "\"").matches());
    assertFalse(strongEtag.matcher("W/\"inactive\"").matches());
    assertFalse(strongEtag.matcher("inactive").matches());

    Map<String, Object> schemas = schemas(document);
    Map<String, Object> statusProperties =
        objectMap(objectMap(schemas.get("BookingCalendarSubscriptionStatus")).get("properties"));
    Map<String, Object> createdProperties =
        objectMap(objectMap(schemas.get("BookingCalendarSubscriptionCreated")).get("properties"));
    assertEquals(Set.of("active", "updatedAt", "subscriptionUrl"), statusProperties.keySet());
    assertEquals("uri", objectMap(statusProperties.get("subscriptionUrl")).get("format"));
    assertTrue(createdProperties.containsKey("subscriptionUrl"));
    assertEquals("uri", objectMap(createdProperties.get("subscriptionUrl")).get("format"));
  }

  private static Map<String, Object> schemas(Map<String, Object> document) {
    return objectMap(objectMap(document.get("components")).get("schemas"));
  }

  private static Map<String, Object> objectMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new AssertionError("Expected an object");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, item) -> result.put((String) key, item));
    return result;
  }

  private static List<Map<String, Object>> objectMapList(Object value) {
    if (!(value instanceof List<?> list)) {
      throw new AssertionError("Expected an array");
    }
    return list.stream().map(ApiV2OpenApiFixtureTest::objectMap).toList();
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
