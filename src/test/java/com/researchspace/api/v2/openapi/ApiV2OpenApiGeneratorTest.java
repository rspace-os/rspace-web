package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.OpenApiOperationDocumentation;
import com.researchspace.api.v2.resource.ResourceAccessSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationCapabilities;
import com.researchspace.model.booking.BookingOwnerHealth;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import io.swagger.v3.core.util.Json31;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiV2OpenApiGeneratorTest {

  private ApiV2OpenApiGenerator generator;

  @BeforeEach
  void setUp() {
    ResourceOperations<ScheduledMaintenance, Long> maintenanceOperations = operationsMock();
    ResourceOperations<User, Long> userOperations = operationsMock();
    ResourceOperations<BookingConfiguration, Long> bookingOperations = operationsMock();
    when(bookingOperations.deleteRequiresIfMatch()).thenReturn(true);
    when(bookingOperations.deleteIsSoft()).thenReturn(true);
    when(bookingOperations.supportsPermanentDelete()).thenReturn(true);
    when(bookingOperations.ifMatchRequiredCode())
        .thenReturn(Optional.of("errors.api.v2.bookingConfiguration.ifMatchRequired"));
    ResourceOperations<TimeSlotBooking, Long> timeSlotBookingOperations = operationsMock();
    ApiV2ResourceSpec<ScheduledMaintenance, Long> maintenance =
        new ApiV2ResourceSpec<>(
            ApiV2MaintenanceResource.DESCRIPTION,
            maintenanceOperations,
            Long::valueOf,
            "create-error",
            "update-error",
            EnumSet.allOf(ResourceOperation.class),
            Map.of(
                ResourceOperation.LIST,
                OpenApiOperationDocumentation.builder()
                    .summary("Browse maintenance windows")
                    .description("Developer-supplied list documentation.")
                    .tag("Operations")
                    .responseDescription(200, "Documented maintenance page.")
                    .extension("x-rspace-audience", "operators")
                    .build()),
            Map.of(
                ResourceOperation.CREATE,
                List.of(
                    ApiV2ErrorMapping.of(
                        ExampleConflictException.class,
                        HttpStatus.CONFLICT,
                        "errors.example.conflict",
                        "The resource conflicts."),
                    ApiV2ErrorMapping.of(
                        SecondConflictException.class,
                        HttpStatus.CONFLICT,
                        "errors.example.second-conflict",
                        "The resource has another conflict."))));
    ApiV2ResourceSpec<User, Long> users =
        new ApiV2ResourceSpec<>(
            ApiV2UserResource.DESCRIPTION,
            userOperations,
            Long::valueOf,
            "create-error",
            "update-error");
    ApiV2ResourceSpec<BookingConfiguration, Long> bookingConfigurations =
        new ApiV2ResourceSpec<>(
            ApiV2BookingConfigurationResource.DESCRIPTION,
            bookingOperations,
            Long::valueOf,
            "create-error",
            "update-error",
            EnumSet.allOf(ResourceOperation.class),
            Map.of(),
            Map.of(),
            CollectionMutationLimits.DEFAULT,
            List.of(),
            Optional.of(
                new ResourceAccessSpec<>(
                    protectedAccess(),
                    BookingConfigurationCapabilities.class,
                    BookingOwnerHealth.class)));
    ApiV2ResourceSpec<TimeSlotBooking, Long> timeSlotBookings =
        new ApiV2ResourceSpec<>(
            ApiV2TimeSlotBookingResource.DESCRIPTION,
            timeSlotBookingOperations,
            Long::valueOf,
            "create-error",
            "update-error");
    ApiV2RelationshipTargetSpec<Instrument, Long> instruments =
        new ApiV2RelationshipTargetSpec<>(
            ApiV2InstrumentResource.DESCRIPTION, Long.class, (ids, actor) -> Map.of());
    ApiV2RelationshipTargetSpec<Instrument, Long> bookingInstruments =
        new ApiV2RelationshipTargetSpec<>(
            ApiV2BookingInstrumentResource.DESCRIPTION, Long.class, (ids, actor) -> Map.of());
    generator =
        new ApiV2OpenApiGenerator(
            new ApiV2ResourceCatalog(
                List.of(maintenance, users, bookingConfigurations, timeSlotBookings),
                List.of(instruments, bookingInstruments)),
            "Test API",
            "2.0.0");
  }

  private static final class ExampleConflictException extends RuntimeException {}

  private static final class SecondConflictException extends RuntimeException {}

  @SuppressWarnings("unchecked")
  private static ProtectedResourceAccess<BookingConfiguration, Long> protectedAccess() {
    return mock(ProtectedResourceAccess.class);
  }

  @Test
  void generatesConcretePathsAndKeepsTargetOnlyResourcesSchemaOnly() {
    Map<String, Object> document = document();
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
    assertEquals(Set.of("apiKey", "bearerAuth", "browserSession"), securitySchemes.keySet());

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
  void documentsAccessOnlyForRegisteredProtectedResources() {
    Map<String, Object> document = document();
    Map<String, Object> paths = objectMap(document.get("paths"));
    String accessPath = "/api/v2/booking-configurations/{id}/access";

    assertEquals(Set.of("get", "put"), objectMap(paths.get(accessPath)).keySet());
    assertTrue(paths.containsKey(accessPath + "/me"));
    assertTrue(paths.containsKey(accessPath + "/grantees"));
    assertTrue(paths.containsKey("/api/v2/booking-settings/access-grantees"));
    assertTrue(paths.containsKey("/api/v2/booking-configuration-targets"));
    assertFalse(paths.containsKey("/api/v2/maintenances/{id}/access"));

    Map<String, Object> schemas = schemas(document);
    assertTrue(schemas.containsKey("ResourceAccessDocument"));
    assertTrue(schemas.containsKey("ResourceAccessReplacement"));
    assertTrue(schemas.containsKey("BookingConfigurationTarget"));
    Map<String, Object> readProperties =
        objectMap(objectMap(schemas.get("BookingConfigurationsRead")).get("properties"));
    assertEquals(
        "#/components/schemas/BookingConfigurationCapabilities",
        objectMap(readProperties.get("capabilities")).get("$ref"));
    assertEquals(
        "#/components/schemas/ResourceRoleSource",
        objectMap(objectMap(readProperties.get("roleSources")).get("items")).get("$ref"));
  }

  @Test
  void documentsVersionedArchiveAndExplicitPermanentDelete() {
    Map<String, Object> paths = objectMap(document().get("paths"));
    Map<String, Object> delete =
        objectMap(objectMap(paths.get("/api/v2/booking-configurations/{id}")).get("delete"));
    List<Map<String, Object>> parameters = objectMapList(delete.get("parameters"));

    assertEquals("Archive one booking-configurations", delete.get("summary"));
    assertTrue(
        parameters.stream()
            .anyMatch(
                parameter ->
                    parameter.get("name").equals("If-Match")
                        && parameter.get("in").equals("header")
                        && parameter.get("required").equals(true)));
    Map<String, Object> permanent =
        parameters.stream()
            .filter(parameter -> parameter.get("name").equals("permanent"))
            .findFirst()
            .orElseThrow();
    assertEquals(false, objectMap(permanent.get("schema")).get("default"));
    assertTrue(objectMap(delete.get("responses")).containsKey("204"));

    Map<String, Object> bulkDelete =
        objectMap(objectMap(paths.get("/api/v2/booking-configurations")).get("delete"));
    assertEquals("Archive matching booking-configurations", bulkDelete.get("summary"));
    assertFalse(
        objectMapList(bulkDelete.get("parameters")).stream()
            .anyMatch(parameter -> parameter.get("name").equals("permanent")));
  }

  @Test
  void documentsCalendarSubscriptionManagementWithTheCurrentUrl() {
    Map<String, Object> document = document();
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
    assertEquals(
        Set.of("200", "401", "403", "404", "406", "429", "500"),
        objectMap(objectMap(management.get("get")).get("responses")).keySet());
    assertEquals(
        Set.of("204", "401", "403", "404", "406", "429", "500"),
        objectMap(objectMap(management.get("delete")).get("responses")).keySet());

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
  void marksTemporaryInstrumentLocationFieldsDeprecatedAndNullable() {
    Map<String, Object> instrumentProperties =
        objectMap(objectMap(schemas(document()).get("InstrumentsRead")).get("properties"));

    for (String field : List.of("parentContainerName", "parentContainerGlobalId")) {
      Map<String, Object> location = objectMap(instrumentProperties.get(field));
      assertEquals(true, location.get("deprecated"));
      assertTrue(
          String.valueOf(location.get("description"))
              .contains("future parentContainer relationship"));
      assertTrue(((List<?>) location.get("type")).contains("null"));
    }
  }

  @Test
  void generatesOperationSpecificSchemasSecurityAndQueryMetadata() {
    Map<String, Object> document = document();
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
    Map<String, Object> conflict = objectMap(objectMap(create.get("responses")).get("409"));
    assertEquals(
        "The resource conflicts. The resource has another conflict.", conflict.get("description"));
    Map<String, Object> conflictMedia =
        objectMap(objectMap(conflict.get("content")).get("application/problem+json"));
    Map<String, Object> conflictExamples = objectMap(conflictMedia.get("examples"));
    assertEquals(
        Set.of("errors.example.conflict", "errors.example.second-conflict"),
        conflictExamples.keySet());
    assertEquals(
        "errors.example.conflict",
        objectMap(objectMap(conflictExamples.get("errors.example.conflict")).get("value"))
            .get("code"));
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

    List<Map<String, Object>> parameters = objectMapList(list.get("parameters"));
    Map<String, Object> where =
        parameters.stream()
            .filter(parameter -> parameter.get("name").equals("where"))
            .findFirst()
            .orElseThrow();
    Map<String, Object> filter = objectMap(where.get("x-rspace-filter"));
    assertEquals(32768, objectMap(where.get("schema")).get("maxLength"));
    assertEquals(50, filter.get("maximumComparisons"));
    assertEquals(10, filter.get("maximumLikeComparisons"));
    assertEquals(1000, filter.get("maximumArguments"));
    assertTrue(objectMap(filter.get("selectors")).containsKey("message"));
    assertFalse(objectMap(filter.get("selectors")).containsKey("canUserLoginNow"));
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
    assertFalse(
        ((List<?>) objectMap(sort.get("x-rspace-sort")).get("fields")).contains("canUserLoginNow"));
    assertEquals(
        1, parameters.stream().filter(parameter -> parameter.get("name").equals("fields")).count());

    Map<String, Object> bookingList =
        objectMap(objectMap(paths.get("/api/v2/booking-configurations")).get("get"));
    Map<String, Object> bookingWhere =
        objectMapList(bookingList.get("parameters")).stream()
            .filter(parameter -> parameter.get("name").equals("where"))
            .findFirst()
            .orElseThrow();
    Map<String, Object> bookingSelectors =
        objectMap(objectMap(bookingWhere.get("x-rspace-filter")).get("selectors"));
    String bookingWhereDescription = (String) bookingWhere.get("description");
    assertTrue(bookingWhereDescription.contains("createdBy.value==me"));
    assertTrue(bookingWhereDescription.contains("authenticated effective subject"));
    assertTrue(bookingSelectors.containsKey("target.name"));
    assertTrue(bookingSelectors.containsKey("target.deleted"));
    assertEquals(List.of("=="), objectMap(bookingSelectors.get("target.deleted")).get("operators"));
    assertTrue(bookingSelectors.containsKey("createdBy.username"));
    assertTrue(bookingSelectors.containsKey("updatedBy.username"));

    Map<String, Object> bookingRelationshipFields =
        objectMap(bookingWhere.get("x-rspace-relationship-fields"));
    assertTrue(
        bookingRelationshipFields
            .keySet()
            .containsAll(List.of("target.id", "target.name", "target.globalId", "target.deleted")));
    Map<String, Object> name = objectMap(bookingRelationshipFields.get("target.name"));
    assertEquals("Name", name.get("title"));
    assertTrue(((List<?>) name.get("operators")).contains("=contains="));
    Map<String, Object> globalId = objectMap(bookingRelationshipFields.get("target.globalId"));
    assertEquals("Global ID", globalId.get("title"));
    assertEquals(List.of(), globalId.get("operators"));
    assertEquals(false, globalId.get("wildcards"));
    assertFalse(bookingSelectors.containsKey("target.globalId"));
    assertFalse(bookingRelationshipFields.containsKey("target.value"));
    assertFalse(bookingRelationshipFields.containsKey("target.relationTo"));

    Map<String, Object> timeSlotBookingList =
        objectMap(objectMap(paths.get("/api/v2/bookings")).get("get"));
    List<Map<String, Object>> timeSlotBookingParameters =
        objectMapList(timeSlotBookingList.get("parameters"));
    Map<String, Object> timeSlotBookingWhere =
        timeSlotBookingParameters.stream()
            .filter(parameter -> parameter.get("name").equals("where"))
            .findFirst()
            .orElseThrow();
    Map<String, Object> timeSlotBookingSelectors =
        objectMap(objectMap(timeSlotBookingWhere.get("x-rspace-filter")).get("selectors"));
    Map<String, Object> requesterId = objectMap(timeSlotBookingSelectors.get("requesterId"));
    assertEquals("integer", objectMap(requesterId.get("schema")).get("type"));
    assertTrue(((List<?>) requesterId.get("operators")).contains("=="));
    Map<String, Object> timeSlotBookingSort =
        timeSlotBookingParameters.stream()
            .filter(parameter -> parameter.get("name").equals("sort"))
            .findFirst()
            .orElseThrow();
    assertFalse(
        ((List<?>) objectMap(timeSlotBookingSort.get("x-rspace-sort")).get("fields"))
            .contains("requesterId"));

    Map<String, Object> schemas = schemas(document);
    Map<String, Object> readProperties =
        objectMap(objectMap(schemas.get("MaintenancesRead")).get("properties"));
    Map<String, Object> createProperties =
        objectMap(objectMap(schemas.get("MaintenancesCreate")).get("properties"));
    assertTrue(readProperties.containsKey("id"));
    assertTrue(readProperties.containsKey("canUserLoginNow"));
    assertFalse(createProperties.containsKey("id"));
    assertFalse(createProperties.containsKey("canUserLoginNow"));
    assertFalse(readProperties.containsKey("property"));
    assertTrue(schemas.containsKey("ApiV2Problem"));
    // No ApiV2BulkError: bulk operations are atomic, so a per-document error list was always empty
    // and only ever produced dead branches in a generated client.
    assertFalse(schemas.containsKey("ApiV2BulkError"));
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
    assertFalse(bookingProperties.containsKey("timezone"));
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
    assertTrue((Boolean) objectMap(bookingReadProperties.get("updatedBy")).get("readOnly"));
    assertTrue(bookingReadProperties.containsKey("timezone"));
    assertFalse(bookingReadProperties.containsKey("timeZone"));
    List<?> nullableTargetOutput =
        (List<?>) objectMap(bookingReadProperties.get("target")).get("anyOf");
    assertEquals("null", objectMap(nullableTargetOutput.get(1)).get("type"));
    List<?> targetOutputVariants = (List<?>) objectMap(nullableTargetOutput.get(0)).get("oneOf");
    Map<String, Object> targetReference = objectMap(targetOutputVariants.get(0));
    List<?> targetReferenceParts = (List<?>) targetReference.get("allOf");
    assertEquals(
        "#/components/schemas/BookingInstrumentsReference",
        objectMap(targetReferenceParts.get(0)).get("$ref"));
    Map<String, Object> targetReferenceProperties =
        objectMap(objectMap(targetReferenceParts.get(1)).get("properties"));
    assertEquals("string", objectMap(targetReferenceProperties.get("globalId")).get("type"));
    assertEquals("^IN\\d+$", objectMap(targetReferenceProperties.get("globalId")).get("pattern"));
    Map<String, Object> instrumentReferenceProperties =
        objectMap(objectMap(schemas.get("InstrumentsReference")).get("properties"));
    assertEquals("integer", objectMap(instrumentReferenceProperties.get("value")).get("type"));

    Map<String, Object> timeSlotBookingReadProperties =
        objectMap(objectMap(schemas.get("BookingsRead")).get("properties"));
    assertTrue(timeSlotBookingReadProperties.containsKey("requesterId"));

    Map<String, Object> bookingUpdateProperties =
        objectMap(objectMap(schemas.get("BookingConfigurationsUpdate")).get("properties"));
    assertFalse(bookingUpdateProperties.containsKey("target"));

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
  void documentsDailyAuditSnapshotContract() {
    Map<String, Object> document = document();
    Map<String, Object> paths = objectMap(document.get("paths"));
    Map<String, Object> audit =
        objectMap(objectMap(paths.get("/api/v2/maintenances/{id}/audit")).get("get"));
    assertEquals("listMaintenancesAuditEvents", audit.get("operationId"));
    assertEquals(2, ((List<?>) audit.get("security")).size());
    assertTrue(objectMap(audit.get("responses")).containsKey("404"));
    List<Map<String, Object>> auditParameters = objectMapList(audit.get("parameters"));
    assertEquals(
        List.of(
            "id",
            "dateFrom",
            "dateTo",
            "actions",
            "snapshotDate",
            "snapshotFingerprint",
            "page",
            "limit"),
        auditParameters.stream().map(parameter -> parameter.get("name")).toList());
    Map<String, Object> auditResponses = objectMap(audit.get("responses"));
    assertEquals(
        List.of("200", "400", "401", "403", "404", "406", "409", "429", "500", "503"),
        auditResponses.keySet().stream().toList());
    Map<String, Object> auditPage =
        objectMap(
            objectMap(objectMap(auditResponses.get("200")).get("content")).get("application/json"));
    Map<String, Object> auditPageSchema = objectMap(auditPage.get("schema"));
    assertTrue(((List<?>) auditPageSchema.get("required")).contains("snapshotDate"));
    assertTrue(((List<?>) auditPageSchema.get("required")).contains("snapshotFingerprint"));
    Map<String, Object> auditPageProperties = objectMap(auditPageSchema.get("properties"));
    assertEquals("date", objectMap(auditPageProperties.get("snapshotDate")).get("format"));
    assertEquals(
        "^[0-9a-f]{64}$", objectMap(auditPageProperties.get("snapshotFingerprint")).get("pattern"));

    Map<String, Object> auditCount =
        objectMap(objectMap(paths.get("/api/v2/maintenances/{id}/audit/count")).get("get"));
    assertEquals(
        List.of("id", "dateFrom", "dateTo", "actions"),
        objectMapList(auditCount.get("parameters")).stream()
            .map(parameter -> parameter.get("name"))
            .toList());
    assertEquals(
        List.of("200", "400", "401", "403", "404", "406", "429", "500"),
        objectMap(auditCount.get("responses")).keySet().stream().toList());
    assertTrue(String.valueOf(audit.get("description")).contains("half-open interval"));
    assertTrue(
        String.valueOf(objectMap(auditResponses.get("400")).get("description"))
            .contains("narrower date range"));
  }

  private static Map<String, Object> schemas(Map<String, Object> document) {
    return objectMap(objectMap(document.get("components")).get("schemas"));
  }

  private Map<String, Object> document() {
    return Json31.mapper()
        .convertValue(generator.generate(), new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private static Map<String, Object> objectMap(Object value) {
    if (!(value instanceof Map<?, ?> map)
        || map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
      throw new AssertionError("Expected an object with string keys");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, item) -> result.put((String) key, item));
    return result;
  }

  private static List<Map<String, Object>> objectMapList(Object value) {
    if (!(value instanceof List<?> list)) {
      throw new AssertionError("Expected an array");
    }
    return list.stream().map(ApiV2OpenApiGeneratorTest::objectMap).toList();
  }

  @SuppressWarnings("unchecked") // Mockito creates an erased interface mock; ID use is test-owned.
  private static <T> ResourceOperations<T, Long> operationsMock() {
    return (ResourceOperations<T, Long>) mock(ResourceOperations.class);
  }
}
