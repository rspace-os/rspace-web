package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.researchspace.api.v2.openapi.ApiV2OpenApiGenerator;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    Map<Path, String> fixtures =
        Map.of(
            MOCKS.resolve("apiV2OpenApi.json"), generatedDocument(),
            MOCKS.resolve("apiV2CalendarEventFilter.json"), calendarEventFilter());
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
                  .map(ApiV2OpenApiFixtureTest::token)
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

  private static String generatedDocument() throws IOException {
    try (var context = ApiV2ResourceConfigTest.newContext()) {
      context.register(ApiV2ResourceConfig.class);
      context.refresh();
      var catalog = context.getBean(ApiV2ResourceCatalog.class);
      JsonNode document =
          Json31.mapper()
              .valueToTree(
                  new ApiV2OpenApiGenerator(catalog, "RSpace REST API v2", "2.0.0").generate());
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
