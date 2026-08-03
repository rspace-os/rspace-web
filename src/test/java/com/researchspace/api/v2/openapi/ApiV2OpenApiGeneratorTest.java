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
import java.util.Set;
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
    assertTrue(paths.containsKey("/api/v2/maintenances/bulk"));
    assertTrue(paths.containsKey("/api/v2/maintenances/{id}"));
    assertTrue(paths.containsKey("/api/v2/maintenances/{id}/audit"));
    assertTrue(paths.containsKey("/api/v2/maintenances/{id}/audit/count"));
    assertFalse(paths.containsKey("/api/v2/{resource}"));
    assertFalse(paths.containsKey("/api/v2/instruments"));

    Map<String, Object> securitySchemes =
        objectMap(objectMap(document.get("components")).get("securitySchemes"));
    assertEquals(Set.of("apiKey", "bearerAuth"), securitySchemes.keySet());

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
    Map<String, Object> bulkCreate =
        objectMap(objectMap(paths.get("/api/v2/maintenances/bulk")).get("post"));

    assertEquals(List.of(), list.get("security"));
    assertEquals(2, ((List<?>) create.get("security")).size());
    assertEquals("createManyMaintenances", bulkCreate.get("operationId"));
    assertTrue(objectMap(bulkCreate.get("responses")).containsKey("201"));
    Map<String, Object> bulkRequestSchema =
        objectMap(
            objectMap(
                    objectMap(objectMap(bulkCreate.get("requestBody")).get("content"))
                        .get("application/json"))
                .get("schema"));
    assertEquals(List.of("docs"), bulkRequestSchema.get("required"));
    assertEquals(false, bulkRequestSchema.get("additionalProperties"));
    Map<String, Object> docs =
        objectMap(objectMap(bulkRequestSchema.get("properties")).get("docs"));
    assertEquals(1, docs.get("minItems"));
    assertEquals(
        "#/components/schemas/MaintenancesCreate", objectMap(docs.get("items")).get("$ref"));
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
    assertTrue(schemas.containsKey("ApiV2AuditEvent"));
    assertEquals(
        "Stable maintenance identifier.", objectMap(readProperties.get("id")).get("description"));
    assertEquals(42L, objectMap(readProperties.get("id")).get("example"));
    assertEquals(List.of(43L), objectMap(readProperties.get("id")).get("examples"));

    Map<String, Object> userProperties =
        objectMap(objectMap(schemas.get("UsersRead")).get("properties"));
    assertEquals("email", objectMap(userProperties.get("email")).get("format"));
    assertTrue(userProperties.containsKey("createdAt"));
    assertFalse(userProperties.containsKey("updatedBy"));
    assertTrue((Boolean) objectMap(userProperties.get("createdAt")).get("readOnly"));
    assertFalse(readProperties.containsKey("createdAt"));

    Map<String, Object> bookingProperties =
        objectMap(objectMap(schemas.get("BookingConfigurationsCreate")).get("properties"));
    assertTrue(bookingProperties.containsKey("timezone"));
    assertFalse(bookingProperties.containsKey("timeZone"));
    Map<String, Object> target = objectMap(bookingProperties.get("target"));
    assertEquals("Booking target", target.get("title"));
    assertNotNull(target.get("allOf"));
    List<?> createTargetParts = (List<?>) target.get("allOf");
    assertTrue(
        objectMap(objectMap(createTargetParts.get(1)).get("properties")).containsKey("globalId"));
    assertFalse(bookingProperties.containsKey("createdAt"));

    Map<String, Object> bookingReadProperties =
        objectMap(objectMap(schemas.get("BookingConfigurationsRead")).get("properties"));
    assertTrue(bookingReadProperties.containsKey("createdAt"));
    assertTrue(bookingReadProperties.containsKey("updatedAt"));
    assertTrue(bookingReadProperties.containsKey("createdBy"));
    assertTrue(bookingReadProperties.containsKey("updatedBy"));
    assertTrue(bookingReadProperties.containsKey("timezone"));
    assertFalse(bookingReadProperties.containsKey("timeZone"));
    List<?> targetOutputVariants =
        (List<?>) objectMap(bookingReadProperties.get("target")).get("oneOf");
    Map<String, Object> targetReference = objectMap(targetOutputVariants.get(0));
    List<?> targetReferenceParts = (List<?>) targetReference.get("allOf");
    assertEquals(
        "#/components/schemas/InstrumentsReference",
        objectMap(targetReferenceParts.get(0)).get("$ref"));
    Map<String, Object> targetReferenceProperties =
        objectMap(objectMap(targetReferenceParts.get(1)).get("properties"));
    assertEquals("string", objectMap(targetReferenceProperties.get("globalId")).get("type"));
    assertEquals("^IN\\d+$", objectMap(targetReferenceProperties.get("globalId")).get("pattern"));
    Map<String, Object> instrumentReferenceProperties =
        objectMap(objectMap(schemas.get("InstrumentsReference")).get("properties"));
    assertEquals("integer", objectMap(instrumentReferenceProperties.get("value")).get("type"));

    Map<String, Object> bookingUpdateProperties =
        objectMap(objectMap(schemas.get("BookingConfigurationsUpdate")).get("properties"));
    List<?> updateTargetVariants =
        (List<?>) objectMap(bookingUpdateProperties.get("target")).get("oneOf");
    assertNotNull(updateTargetVariants);
    assertNotNull(objectMap(updateTargetVariants.get(1)).get("allOf"));

    Map<String, Object> components = objectMap(document.get("components"));
    Map<String, Object> badRequest =
        objectMap(objectMap(components.get("responses")).get("BadRequest"));
    Map<String, Object> problemMedia =
        objectMap(objectMap(badRequest.get("content")).get("application/problem+json"));
    Map<String, Object> example = objectMap(problemMedia.get("example"));
    assertEquals(List.of("title", "status", "code", "detail"), example.keySet().stream().toList());
    assertTrue(objectMap(components.get("headers")).containsKey("RateLimitRemaining"));

    Map<String, Object> audit =
        objectMap(objectMap(paths.get("/api/v2/maintenances/{id}/audit")).get("get"));
    assertEquals("listMaintenancesAuditEvents", audit.get("operationId"));
    assertEquals(2, ((List<?>) audit.get("security")).size());
    assertTrue(objectMap(audit.get("responses")).containsKey("404"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> auditParameters = (List<Map<String, Object>>) audit.get("parameters");
    assertEquals(
        List.of("id", "dateFrom", "dateTo", "actions", "page", "limit"),
        auditParameters.stream().map(parameter -> parameter.get("name")).toList());
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
