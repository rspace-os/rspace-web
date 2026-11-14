package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.researchspace.featureflags.FeatureFlagResourceOperations;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.FilterSchema;
import com.researchspace.model.collection.Operator;
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
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Keeps the frontend's REST API v2 OpenAPI fixture equal to the document the backend generates, so
 * frontend tests run against the published collection contract instead of a hand-written copy.
 */
class ApiV2OpenApiDocumentExportTest {

  private static final Path MOCKS = Path.of("src/main/webapp/ui/src/__tests__/mocks");
  private static final String UPDATE_PROPERTY = "updateApiV2OpenApiFixture";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void tagsMatchOperationsAndFormCompleteGroups() throws Exception {
    assertTagDocumentation(MAPPER.readTree(generatedDocument()));
  }

  @Test
  void frontendFixturesMatchTheGeneratedContract() throws IOException {
    Map<Path, String> fixtures =
        Map.of(
            Path.of("src/main/webapp/ui/src/modules/common/api/v2/openapi.generated.json"),
            publishedDocument(),
            MOCKS.resolve("apiV2CalendarEventFilter.json"),
            calendarEventFilter());
    for (Map.Entry<Path, String> fixture : fixtures.entrySet()) {
      if (Boolean.getBoolean(UPDATE_PROPERTY)) {
        Files.createDirectories(fixture.getKey().getParent());
        Files.writeString(fixture.getKey(), fixture.getValue());
      } else if (!Files.exists(fixture.getKey())
          || !fixture.getValue().equals(Files.readString(fixture.getKey()))) {
        fail(
            fixture.getKey()
                + " is out of date with the REST API v2 resources. Regenerate it with: mvn test"
                + " -Dfast=true -Dtest=ApiV2OpenApiDocumentExportTest -D"
                + UPDATE_PROPERTY
                + "=true");
      }
    }
  }

  @Test
  void generatedDocumentIncludesAnnotatedBookingRoutes() throws IOException {
    JsonNode paths = MAPPER.readTree(generatedDocument()).path("paths");
    assertTrue(paths.has("/api/v2/booking-settings/admin"));
    assertEquals(
        "#/components/headers/ETag",
        paths
            .path("/api/v2/booking-settings/admin")
            .path("patch")
            .path("responses")
            .path("200")
            .path("headers")
            .path("ETag")
            .path("$ref")
            .asText());
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
        "createBookingCalendarSubscription", objectMap(management.get("post")).get("operationId"));
    assertEquals(
        "revokeBookingCalendarSubscription",
        objectMap(management.get("delete")).get("operationId"));
    Map<String, Object> get = objectMap(management.get("get"));
    Map<String, Object> post = objectMap(management.get("post"));
    Map<String, Object> delete = objectMap(management.get("delete"));
    assertEquals(
        Set.of("200", "401", "404", "406", "429", "500"), objectMap(get.get("responses")).keySet());
    assertEquals(
        Set.of("200", "201", "401", "404", "406", "409", "429", "500"),
        objectMap(post.get("responses")).keySet());
    assertEquals(
        Set.of("204", "401", "404", "406", "429", "500"),
        objectMap(delete.get("responses")).keySet());

    Map<String, Object> rotate =
        objectMap(
            objectMap(
                    paths.get(
                        "/api/v2/booking-configurations/{configurationId}/calendar-subscription/rotate"))
                .get("post"));
    assertEquals(
        Set.of("200", "400", "401", "404", "406", "409", "412", "428", "429", "500"),
        objectMap(rotate.get("responses")).keySet());
    Map<String, Object> ifMatch =
        objectMapList(rotate.get("parameters")).stream()
            .filter(parameter -> parameter.get("name").equals("If-Match"))
            .findFirst()
            .orElseThrow();
    assertEquals("header", ifMatch.get("in"));
    assertEquals(true, ifMatch.get("required"));
    assertEquals("string", objectMap(ifMatch.get("schema")).get("type"));

    for (Map<String, Object> operation : List.of(get, post, rotate)) {
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

  @Test
  void calendarRoutesDocumentFeatureOffAsNotFound() throws IOException {
    JsonNode paths = MAPPER.readTree(generatedDocument()).path("paths");
    for (String path :
        List.of(
            "/api/v2/booking-configurations/{configurationId}/calendar-subscription",
            "/api/v2/users/me/booking-calendar-subscription",
            "/api/v2/bookings/{bookingId}/calendar-file")) {
      assertTrue(paths.has(path), path);
      for (JsonNode operation : paths.path(path)) {
        assertTrue(operation.path("responses").has("404"), path);
        assertFalse(operation.path("responses").has("403"), path);
      }
    }
    JsonNode download = paths.path("/api/v2/bookings/{bookingId}/calendar-file").path("get");
    assertEquals("downloadBookingCalendarFile", download.path("operationId").asText());
    assertEquals(
        "string",
        download
            .path("responses")
            .path("200")
            .path("content")
            .path("text/calendar")
            .path("schema")
            .path("type")
            .asText());
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
    return list.stream().map(ApiV2OpenApiDocumentExportTest::objectMap).toList();
  }

  /**
   * The Calendar endpoints parse {@code where} against their own description, which the OpenAPI
   * document does not publish, so their selectors and operators get a fixture of their own.
   */
  private static String calendarEventFilter() throws IOException {
    ObjectNode selectors = MAPPER.createObjectNode();
    ApiV2TimeSlotBookingResource.calendarFilterDescription(AccessFunction.authenticated())
        .schema()
        .filters()
        .stream()
        .sorted(Comparator.comparing(FilterSchema::selector))
        .forEach(
            filter -> {
              ObjectNode selector = selectors.putObject(filter.selector());
              filter.operators().stream()
                  .map(ApiV2OpenApiDocumentExportTest::token)
                  .sorted()
                  .forEach(selector.putArray("operators")::add);
              selector.put("wildcards", filter.supportsWildcards());
            });
    ObjectNode document = MAPPER.createObjectNode();
    document.set("selectors", selectors);
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n";
  }

  /** The RSQL spelling of an operator, as {@code where} publishes it. */
  private static String token(Operator operator) {
    return switch (operator) {
      case EQUAL -> "==";
      case NOT_EQUAL -> "!=";
      case GREATER_THAN -> "=gt=";
      case GREATER_THAN_OR_EQUAL -> "=ge=";
      case LESS_THAN -> "=lt=";
      case LESS_THAN_OR_EQUAL -> "=le=";
      case IN -> "=in=";
      case NOT_IN -> "=out=";
      case CONTAINS -> "=contains=";
      case LIKE -> "=like=";
      case EXISTS -> "=exists=";
    };
  }

  /** Component sections whose entries exist only to be referenced by operations and schemas. */
  private static final Set<String> SHARED_SECTIONS =
      Set.of("parameters", "responses", "examples", "x-rspace-filters", "x-rspace-access-policies");

  /**
   * The generated document as its readers see it: references into the shared component sections are
   * resolved, while schema and header references stay.
   */
  private static String generatedDocument() throws IOException {
    JsonNode document = MAPPER.readTree(publishedDocument());
    return MAPPER.writeValueAsString(resolveShared(document, document));
  }

  private static JsonNode resolveShared(JsonNode document, JsonNode node) {
    if (node.isObject()) {
      String[] ref = node.path("$ref").asText("").split("/");
      if (node.size() == 1
          && ref.length == 4
          && "#".equals(ref[0])
          && "components".equals(ref[1])
          && SHARED_SECTIONS.contains(ref[2])) {
        JsonNode target = document.path("components").path(ref[2]).path(ref[3]);
        assertFalse(target.isMissingNode(), "Unresolved OpenAPI reference " + node);
        return resolveShared(document, target);
      }
      ObjectNode result = MAPPER.createObjectNode();
      node.fields()
          .forEachRemaining(
              field -> result.set(field.getKey(), resolveShared(document, field.getValue())));
      return result;
    }
    if (node.isArray()) {
      ArrayNode result = MAPPER.createArrayNode();
      node.forEach(element -> result.add(resolveShared(document, element)));
      return result;
    }
    return node;
  }

  /** The generated document as published, with its component references. */
  private static String publishedDocument() throws IOException {
    try (var context = ApiV2ResourceConfigTest.newContext();
        var handlers = ApiV2TestHandlerMappings.newContext()) {
      context.register(ApiV2ResourceConfig.class, FeatureFlagResourceOperations.class);
      context.refresh();
      var catalog = context.getBean(ApiV2ResourceCatalog.class);
      JsonNode document =
          Json31.mapper()
              .valueToTree(
                  new ApiV2OpenApiDocumentService(
                          new ApiV2OpenApiGenerator(
                              catalog,
                              com.researchspace.api.v2.openapi.ApiV2OpenApiTestText.english(),
                              "RSpace REST API v2",
                              "2.0.0"),
                          context.getBean(ApiV2EndpointCatalog.class),
                          () -> Stream.of(ApiV2TestHandlerMappings.mapping(handlers)))
                      .generate());
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(sorted(document)) + "\n";
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
