package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.inventory.api.v2.ApiV2InstrumentResource;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.inventory.Instrument;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiV2OpenApiGeneratorTest {

  private ApiV2OpenApiGenerator generator;

  @BeforeEach
  void setUp() {
    @SuppressWarnings("unchecked")
    ResourceOperations<ScheduledMaintenance, Long> maintenanceOperations =
        mock(ResourceOperations.class);
    @SuppressWarnings("unchecked")
    ResourceOperations<User, Long> userOperations = mock(ResourceOperations.class);
    @SuppressWarnings("unchecked")
    ResourceOperations<BookingConfiguration, Long> bookingOperations =
        mock(ResourceOperations.class);
    ApiV2ResourceSpec<ScheduledMaintenance, Long> maintenance =
        new ApiV2ResourceSpec<>(
            ApiV2MaintenanceResource.DESCRIPTION,
            maintenanceOperations,
            Long::valueOf,
            "create-error",
            "update-error",
            java.util.EnumSet.allOf(ResourceOperation.class),
            Map.of(),
            Map.of(
                ResourceOperation.LIST,
                OpenApiOperationDocumentation.builder()
                    .summary("Browse maintenance windows")
                    .description("Developer-supplied list documentation.")
                    .tag("Operations")
                    .responseDescription(200, "Documented maintenance page.")
                    .extension("x-rspace-audience", "operators")
                    .build()));
    ApiV2ResourceSpec<User, Long> users =
        new ApiV2ResourceSpec<>(
            ApiV2UserResource.DESCRIPTION,
            userOperations,
            Long::valueOf,
            "create-error",
            "update-error");
    ApiV2ResourceSpec<BookingConfiguration, Long> bookings =
        new ApiV2ResourceSpec<>(
            ApiV2BookingConfigurationResource.DESCRIPTION,
            bookingOperations,
            Long::valueOf,
            "create-error",
            "update-error");
    ApiV2RelationshipTargetSpec<Instrument, Long> instruments =
        new ApiV2RelationshipTargetSpec<>(
            ApiV2InstrumentResource.DESCRIPTION, (id, actor) -> Optional.empty());
    generator =
        new ApiV2OpenApiGenerator(
            new ApiV2ResourceCatalog(List.of(maintenance, users, bookings), List.of(instruments)),
            "Test API",
            "2.0.0");
  }

  @Test
  void generatesConcretePathsAndKeepsTargetOnlyResourcesSchemaOnly() {
    Map<String, Object> document = generator.generate();
    Map<String, Object> paths = objectMap(document.get("paths"));

    assertEquals("3.1.0", document.get("openapi"));
    assertTrue(paths.containsKey("/api/v2/maintenances"));
    assertTrue(paths.containsKey("/api/v2/maintenances/count"));
    assertTrue(paths.containsKey("/api/v2/maintenances/{id}"));
    assertFalse(paths.containsKey("/api/v2/{resource}"));
    assertFalse(paths.containsKey("/api/v2/instruments"));

    Map<String, Object> users = objectMap(paths.get("/api/v2/users"));
    assertTrue(users.containsKey("get"));
    assertTrue(users.containsKey("post"));
    assertTrue(users.containsKey("patch"));
    assertTrue(users.containsKey("delete"));

    Map<String, Object> schemas = schemas(document);
    assertTrue(schemas.containsKey("InstrumentsRead"));
    assertTrue(schemas.containsKey("InstrumentsReference"));
    assertFalse(schemas.containsKey("InstrumentsCreate"));
  }

  @Test
  void generatesOperationSpecificSchemasSecurityAndQueryMetadata() {
    Map<String, Object> document = generator.generate();
    Map<String, Object> paths = objectMap(document.get("paths"));
    Map<String, Object> collection = objectMap(paths.get("/api/v2/maintenances"));
    Map<String, Object> list = objectMap(collection.get("get"));
    Map<String, Object> create = objectMap(collection.get("post"));

    assertEquals(List.of(), list.get("security"));
    assertFalse(((List<?>) create.get("security")).isEmpty());
    assertNotNull(list.get("x-rspace-access"));
    assertEquals("Browse maintenance windows", list.get("summary"));
    assertEquals("Developer-supplied list documentation.", list.get("description"));
    assertEquals(List.of("Operations"), list.get("tags"));
    assertEquals("operators", list.get("x-rspace-audience"));

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> parameters = (List<Map<String, Object>>) list.get("parameters");
    Map<String, Object> where =
        parameters.stream()
            .filter(parameter -> parameter.get("name").equals("where"))
            .findFirst()
            .orElseThrow();
    Map<String, Object> filter = objectMap(where.get("x-rspace-filter"));
    assertEquals(50, filter.get("maximumComparisons"));
    assertEquals(10, filter.get("maximumLikeComparisons"));
    assertTrue(objectMap(filter.get("selectors")).containsKey("message"));
    Map<String, Object> messageFilter =
        objectMap(objectMap(filter.get("selectors")).get("message"));
    assertTrue(((List<?>) messageFilter.get("operators")).contains("=like="));
    assertFalse(((List<?>) messageFilter.get("operators")).contains("LIKE"));

    Map<String, Object> sort =
        parameters.stream()
            .filter(parameter -> parameter.get("name").equals("sort"))
            .findFirst()
            .orElseThrow();
    assertEquals(List.of("startDate", "id"), objectMap(sort.get("x-rspace-sort")).get("default"));
    assertEquals(
        1, parameters.stream().filter(parameter -> parameter.get("name").equals("fields")).count());

    Map<String, Object> schemas = schemas(document);
    Map<String, Object> readProperties =
        objectMap(objectMap(schemas.get("MaintenancesRead")).get("properties"));
    Map<String, Object> createProperties =
        objectMap(objectMap(schemas.get("MaintenancesCreate")).get("properties"));
    assertTrue(readProperties.containsKey("id"));
    assertFalse(createProperties.containsKey("id"));
    assertFalse(readProperties.containsKey("property"));
    assertTrue(schemas.containsKey("ApiV2Problem"));
    assertTrue(schemas.containsKey("ApiV2BulkError"));
    assertEquals(
        "Stable maintenance identifier.", objectMap(readProperties.get("id")).get("description"));
    assertEquals(42L, objectMap(readProperties.get("id")).get("example"));
    assertEquals(List.of(43L), objectMap(readProperties.get("id")).get("examples"));

    Map<String, Object> userProperties =
        objectMap(objectMap(schemas.get("UsersRead")).get("properties"));
    assertEquals("email", objectMap(userProperties.get("email")).get("format"));

    Map<String, Object> bookingProperties =
        objectMap(objectMap(schemas.get("BookingConfigurationsCreate")).get("properties"));
    Map<String, Object> target = objectMap(bookingProperties.get("target"));
    assertEquals("Booking target", target.get("title"));
    assertNotNull(target.get("$ref"));

    Map<String, Object> bookingUpdateProperties =
        objectMap(objectMap(schemas.get("BookingConfigurationsUpdate")).get("properties"));
    assertNotNull(objectMap(bookingUpdateProperties.get("target")).get("oneOf"));

    Map<String, Object> components = objectMap(document.get("components"));
    Map<String, Object> badRequest =
        objectMap(objectMap(components.get("responses")).get("BadRequest"));
    Map<String, Object> problemMedia =
        objectMap(objectMap(badRequest.get("content")).get("application/problem+json"));
    Map<String, Object> example = objectMap(problemMedia.get("example"));
    assertEquals(List.of("title", "status", "code", "detail"), example.keySet().stream().toList());
    assertTrue(objectMap(components.get("headers")).containsKey("RateLimitRemaining"));
  }

  @Test
  void exportsTheSameDocumentAsJson(@TempDir Path directory) throws Exception {
    Path output = directory.resolve("nested/openapi.json");
    ApiV2OpenApiDocumentService documents =
        new ApiV2OpenApiDocumentService(generator, java.util.stream.Stream::empty);
    new ApiV2OpenApiExporter(documents, new ObjectMapper()).writeJson(output);

    Map<?, ?> exported = new ObjectMapper().readValue(Files.readString(output), Map.class);
    assertEquals("3.1.0", exported.get("openapi"));
    assertTrue(((Map<?, ?>) exported.get("paths")).containsKey("/api/v2/maintenances"));
  }

  private static Map<String, Object> schemas(Map<String, Object> document) {
    return objectMap(objectMap(document.get("components")).get("schemas"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> objectMap(Object value) {
    return (Map<String, Object>) value;
  }
}
