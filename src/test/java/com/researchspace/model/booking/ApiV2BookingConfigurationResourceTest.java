package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.resource.ApiV2DocumentParser;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.FieldSchema;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterSelector;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipInputForm;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRenderer;
import com.researchspace.model.collection.WriteOperation;
import com.researchspace.model.resourceaccess.ResourceRoleSource;
import com.researchspace.model.resourceaccess.ResourceRoleSourceKind;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ApiV2BookingConfigurationResourceTest {

  private static final List<String> COMMON_RESOURCE_FIELDS =
      List.of(
          "id",
          "enabled",
          "state",
          "timezone",
          "slotGranularityMinutes",
          "openingHours",
          "bufferBeforeMinutes",
          "bufferAfterMinutes",
          "maxBookingDurationMinutes",
          "allowDoubleBooking",
          "configurationVersion",
          "createdByName",
          "effectiveRole",
          "roleSources",
          "capabilities");
  private static final List<String> AUDIT_FIELDS = List.of("createdAt", "updatedAt");
  private static final ResourceRegistry REGISTRY =
      new ResourceRegistry(
          List.of(
              com.researchspace.inventory.model.ApiV2ContainerResource.DESCRIPTION,
              ApiV2BookingConfigurationResource.DESCRIPTION,
              ApiV2UserResource.DESCRIPTION,
              ApiV2InstrumentResource.DESCRIPTION));

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void exposesImmutableBooleanCapabilitiesAndAnEmptyUnpreparedProjection() throws Exception {
    BookingConfiguration configuration = new BookingConfiguration();
    assertEquals(Map.of(), configuration.getCapabilities());

    BookingConfigurationCapabilities capabilities =
        mapper.readValue("{\"canEditConfiguration\":true}", BookingConfigurationCapabilities.class);
    configuration.prepareAccessProjection("OWNER", List.of(), capabilities, null);

    Map<String, Object> values = configuration.getCapabilities();
    assertEquals(Boolean.TRUE, values.get("canEditConfiguration"));
    assertEquals(Boolean.FALSE, values.get("canViewAccess"));
    assertTrue(values.values().stream().allMatch(Boolean.class::isInstance));
    assertThrows(UnsupportedOperationException.class, () -> values.put("canViewAccess", true));
  }

  @Test
  void limitsBulkMutationsForBookingConfigurations() {
    assertEquals(50, ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkCreateRows());
    assertEquals(1000, ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkUpdateDeleteRows());
  }

  @Test
  void definesSchedulingFieldsAndTheCreateOnlyTargetRelationship() {
    assertEquals(
        Stream.of(COMMON_RESOURCE_FIELDS, List.of("ownerHealth"), AUDIT_FIELDS)
            .flatMap(List::stream)
            .toList(),
        ApiV2BookingConfigurationResource.DESCRIPTION.fields().stream()
            .map(field -> field.name())
            .toList());
    assertEquals(
        List.of(
            "enabled",
            "state",
            "slotGranularityMinutes",
            "openingHours",
            "bufferBeforeMinutes",
            "bufferAfterMinutes",
            "maxBookingDurationMinutes",
            "allowDoubleBooking"),
        List.copyOf(
            ApiV2BookingConfigurationResource.DESCRIPTION.writableFields(WriteOperation.UPDATE)));
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .writableFields(WriteOperation.CREATE)
            .contains("timezone"));
    assertEquals(
        List.of("target", "location", "createdBy", "updatedBy"),
        ApiV2BookingConfigurationResource.DESCRIPTION.relationships().stream()
            .map(relationship -> relationship.name())
            .toList());
    assertInstanceOf(
        FilterSelector.Property.class,
        ApiV2BookingConfigurationResource.DESCRIPTION
            .findFilterSelector("createdAt")
            .orElseThrow());
    assertInstanceOf(
        FilterSelector.Property.class,
        ApiV2BookingConfigurationResource.DESCRIPTION
            .findFilterSelector("updatedAt")
            .orElseThrow());
    FilterSelector.Property timezone =
        assertInstanceOf(
            FilterSelector.Property.class,
            ApiV2BookingConfigurationResource.DESCRIPTION
                .findFilterSelector("timezone")
                .orElseThrow());
    assertEquals("timeZone", timezone.property());
    assertInstanceOf(
        FilterSelector.RelationshipPart.class,
        ApiV2BookingConfigurationResource.DESCRIPTION
            .findFilterSelector("createdBy.value")
            .orElseThrow());
    assertInstanceOf(
        FilterSelector.RelationshipPart.class,
        ApiV2BookingConfigurationResource.DESCRIPTION
            .findFilterSelector("updatedBy.value")
            .orElseThrow());
    var target = ApiV2BookingConfigurationResource.DESCRIPTION.requireRelationship("target");
    assertFalse(target.acceptsInput(WriteOperation.UPDATE, RelationshipInputForm.GLOBAL_ID));
    assertTrue(target.writableOn(WriteOperation.CREATE));
    assertFalse(target.writableOn(WriteOperation.UPDATE));
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION.requireRelationship("createdBy").nullable());
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION.requireRelationship("updatedBy").nullable());
  }

  @Test
  void exposesLifecycleStateForFilteringAndSorting() {
    assertInstanceOf(
        FilterSelector.Property.class,
        ApiV2BookingConfigurationResource.DESCRIPTION.requirePublicFilterSelector("state"));
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION.schema().filters().stream()
            .anyMatch(filter -> filter.selector().equals("state")));
    assertTrue(ApiV2BookingConfigurationResource.DESCRIPTION.requireField("state").sortable());
  }

  @Test
  void declaresAuthenticatedTransportAccessAndLeavesRoleAuthorityToTheManager() {
    User member = mock(User.class);
    User sysadmin = mock(User.class);
    when(sysadmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);

    assertFalse(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .accessPolicy()
            .updateAccess()
            .check(new AccessContext(member, Operation.UPDATE, "booking-configurations", 42L))
            .isDenied());
    assertFalse(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .accessPolicy()
            .updateAccess()
            .check(new AccessContext(sysadmin, Operation.UPDATE, "booking-configurations", 42L))
            .isDenied());
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .accessPolicy()
            .updateAccess()
            .check(new AccessContext(null, Operation.UPDATE, "booking-configurations", 42L))
            .isDenied());
    assertFalse(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .accessPolicy()
            .readAccess()
            .check(new AccessContext(member, Operation.READ, "booking-configurations"))
            .isDenied());
  }

  @Test
  void parsesAndAppliesCreateDocuments() throws Exception {
    ParsedDocument document =
        ApiV2DocumentParser.parse(
            mapper.readTree(
                """
                {
                  "enabled": true,
                  "slotGranularityMinutes": 15,
                  "openingHours": [{"dayOfWeek": 1, "start": "08:00", "end": "18:00"}],
                  "bufferBeforeMinutes": 10,
                  "bufferAfterMinutes": 20,
                  "maxBookingDurationMinutes": 120,
                  "allowDoubleBooking": true,
                  "target": {"relationTo": "instruments", "value": 12}
                }
                """),
            ApiV2BookingConfigurationResource.DESCRIPTION,
            WriteOperation.CREATE,
            "errors.api.v2.bookingConfiguration.create",
            new AccessContext(null, Operation.CREATE, "booking-configurations"));
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("Europe/Berlin");

    ApiV2BookingConfigurationResource.DESCRIPTION.apply(
        configuration,
        Map.of(
            "enabled",
            true,
            "slotGranularityMinutes",
            15L,
            "openingHours",
            List.of(new BookingOpeningHoursEntry(1, "08:00", "18:00")),
            "bufferBeforeMinutes",
            10L,
            "bufferAfterMinutes",
            20L,
            "maxBookingDurationMinutes",
            120L,
            "allowDoubleBooking",
            true),
        WriteOperation.CREATE);

    assertTrue(configuration.isEnabled());
    assertEquals("Europe/Berlin", configuration.getTimeZone());
    assertTrue(configuration.isTimeZoneValid());
    Map<String, Object> rendered =
        ApiV2BookingConfigurationResource.DESCRIPTION.toDocument(configuration);
    assertEquals(
        Stream.concat(COMMON_RESOURCE_FIELDS.stream(), AUDIT_FIELDS.stream()).toList(),
        List.copyOf(rendered.keySet()));
    assertNull(rendered.get("id"));
    assertEquals(true, rendered.get("enabled"));
    assertEquals("ACTIVE", rendered.get("state"));
    assertEquals("Europe/Berlin", rendered.get("timezone"));
    assertEquals(15L, rendered.get("slotGranularityMinutes"));
    assertEquals(
        List.of(Map.of("dayOfWeek", 1, "start", "08:00", "end", "18:00")),
        rendered.get("openingHours"));
    assertEquals(10L, rendered.get("bufferBeforeMinutes"));
    assertEquals(20L, rendered.get("bufferAfterMinutes"));
    assertEquals(120L, rendered.get("maxBookingDurationMinutes"));
    assertEquals(true, rendered.get("allowDoubleBooking"));
    assertEquals(0L, rendered.get("configurationVersion"));
    assertNull(rendered.get("createdAt"));
    assertNull(rendered.get("updatedAt"));
    assertEquals(
        new ResourceReference<>(BookableTargetType.INSTRUMENT, 12L),
        document.values().get("target"));
    assertEquals(15L, document.values().get("slotGranularityMinutes"));
    assertEquals(120L, document.values().get("maxBookingDurationMinutes"));
  }

  @Test
  void defensivelyCopiesLifecycleDatesOnAssignmentAndRead() {
    BookingConfiguration configuration = new BookingConfiguration();
    Date supplied = new Date(1_000L);
    configuration.setCreatedAt(supplied);
    configuration.setUpdatedAt(supplied);

    supplied.setTime(2_000L);
    assertEquals(1_000L, configuration.getCreatedAt().getTime());
    assertEquals(1_000L, configuration.getUpdatedAt().getTime());

    configuration.getCreatedAt().setTime(3_000L);
    configuration.getUpdatedAt().setTime(3_000L);
    assertEquals(1_000L, configuration.getCreatedAt().getTime());
    assertEquals(1_000L, configuration.getUpdatedAt().getTime());
  }

  @Test
  void rendersAuditUsersAsReadOnlyUserRelationships() {
    User creator = mock(User.class);
    User updater = mock(User.class);
    when(creator.getId()).thenReturn(21L);
    when(creator.getFullName()).thenReturn("Grace Hopper");
    when(creator.getUsername()).thenReturn("grace");
    when(updater.getId()).thenReturn(22L);
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setCreatedBy(creator);
    configuration.setUpdatedBy(updater);
    ResourceRegistry registry =
        new ResourceRegistry(
            List.of(
                ApiV2BookingConfigurationResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION,
                ApiV2BookingInstrumentResource.DESCRIPTION,
                com.researchspace.inventory.model.ApiV2ContainerResource.DESCRIPTION));

    Map<String, Object> rendered =
        new ResourceRenderer(registry)
            .render(
                configuration,
                ApiV2BookingConfigurationResource.DESCRIPTION,
                FieldSelection.all(),
                IncludeTree.empty());

    assertEquals(Map.of("relationTo", "users", "value", 21L), rendered.get("createdBy"));
    assertEquals("Grace Hopper (grace)", rendered.get("createdByName"));
    assertEquals(Map.of("relationTo", "users", "value", 22L), rendered.get("updatedBy"));
    assertFalse(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .writableFields(WriteOperation.CREATE)
            .contains("createdBy"));
  }

  @Test
  void keepsTheConfigurationAndRendersAnUnavailableTargetAsNull() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(7L);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L));
    ResourceRegistry registry =
        new ResourceRegistry(
            List.of(
                ApiV2BookingConfigurationResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION,
                ApiV2BookingInstrumentResource.DESCRIPTION,
                com.researchspace.inventory.model.ApiV2ContainerResource.DESCRIPTION));

    Map<String, Object> rendered =
        new ResourceRenderer(registry)
            .render(
                configuration,
                ApiV2BookingConfigurationResource.DESCRIPTION,
                FieldSelection.all(),
                IncludeTree.empty(),
                (resourceName, id) -> Optional.empty());

    assertEquals(7L, rendered.get("id"));
    assertTrue(rendered.containsKey("target"));
    assertNull(rendered.get("target"));
  }

  @Test
  void acceptsTimeZoneOnlyOnCreateAndKeepsInternalValidation() throws Exception {
    ParsedDocument created =
        ApiV2DocumentParser.parse(
            mapper.readTree(
                "{\"enabled\":true,\"timezone\":\"Europe/Berlin\",\"target\":{\"relationTo\":\"instruments\",\"value\":12}}"),
            ApiV2BookingConfigurationResource.DESCRIPTION,
            WriteOperation.CREATE,
            "errors.api.v2.bookingConfiguration.create",
            new AccessContext(null, Operation.CREATE, "booking-configurations"));
    assertEquals("Europe/Berlin", created.values().get("timezone"));

    updateViolation(mapper.readTree("{\"timezone\":\"Europe/Berlin\"}"));

    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("Not/A_Zone");
    assertFalse(configuration.isTimeZoneValid());
  }

  @Test
  void rejectsTargetPatches() throws Exception {
    updateViolation(mapper.readTree("{\"target\":\"IN12\"}"));
  }

  @Test
  void declaresSchedulingAndRoleSourceChildrenWithoutQueryOperators() {
    FieldSchema hours =
        ApiV2BookingConfigurationResource.DESCRIPTION
            .requireField("openingHours")
            .schema(ApiV2BookingConfigurationResource.DESCRIPTION.accessPolicy());
    assertEquals(
        List.of("dayOfWeek", "start", "end"),
        hours.fields().stream().map(FieldSchema::name).toList());
    assertTrue(hours.fields().stream().allMatch(FieldSchema::requiredOnCreate));
    assertEquals(1, hours.writeConstraints().get("minItems"));
    assertEquals(7, hours.writeConstraints().get("maxItems"));
    assertTrue(hours.filterOperators().isEmpty());
    assertFalse(hours.sortable());
    assertFalse(hours.nullable());
    FieldSchema sources =
        ApiV2BookingConfigurationResource.DESCRIPTION
            .requireField("roleSources")
            .schema(ApiV2BookingConfigurationResource.DESCRIPTION.accessPolicy());
    assertEquals(
        List.of("kind", "role", "grantee"),
        sources.fields().stream().map(FieldSchema::name).toList());
    FieldSchema grantee =
        sources.fields().stream()
            .filter(field -> field.name().equals("grantee"))
            .findFirst()
            .orElseThrow();
    assertEquals(
        List.of("kind", "key", "id", "name"),
        grantee.fields().stream().map(FieldSchema::name).toList());
  }

  @Test
  void replacementRowsRequireEveryChildAndCollectionsRejectExplicitNull() throws Exception {
    JsonNode missingEnd =
        mapper.readTree("{\"openingHours\":[{\"dayOfWeek\":1,\"start\":\"09:00\"}]}");
    JsonNode explicitNull = mapper.readTree("{\"openingHours\":null}");
    DocumentValidationException missing = updateViolation(missingEnd);
    assertEquals("openingHours[0].end", missing.getViolations().get(0).field());
    assertEquals(
        DocumentValidationException.Reason.REQUIRED, missing.getViolations().get(0).reason());
    DocumentValidationException nullValue = updateViolation(explicitNull);
    assertEquals(
        DocumentValidationException.Reason.NULL_NOT_ALLOWED,
        nullValue.getViolations().get(0).reason());
    JsonNode legacy = mapper.readTree("{\"openingStart\":\"09:00\"}");
    assertEquals(
        DocumentValidationException.Reason.UNKNOWN_FIELD,
        updateViolation(legacy).getViolations().get(0).reason());
    JsonNode wrongKind = mapper.readTree("{\"openingHours\":1}");
    JsonNode empty = mapper.readTree("{\"openingHours\":[]}");
    DocumentValidationException wrongType = updateViolation(wrongKind);
    assertEquals(
        DocumentValidationException.Reason.WRONG_TYPE, wrongType.getViolations().get(0).reason());
    DocumentValidationException emptySchedule = updateViolation(empty);
    assertEquals(
        DocumentValidationException.Reason.INVALID_VALUE,
        emptySchedule.getViolations().get(0).reason());
  }

  @Test
  void roleSourceProjectionPreservesGranteeIdsAndOmitsInternalProperties() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.prepareAccessProjection(
        "BOOKER",
        List.of(
            ResourceRoleSource.implicit("BOOKER"),
            new ResourceRoleSource(ResourceRoleSourceKind.DIRECT, "BOOKER", "user:1", "Person"),
            new ResourceRoleSource(ResourceRoleSourceKind.GROUP, "BOOKER", "group:12", "Lab"),
            new ResourceRoleSource(
                ResourceRoleSourceKind.AUDIENCE, "BOOKER", "audience:all-users", "All users")),
        null,
        null);
    JsonNode sources =
        mapper.valueToTree(
            ApiV2BookingConfigurationResource.DESCRIPTION
                .toDocument(configuration)
                .get("roleSources"));
    assertFalse(sources.get(0).has("grantee"));
    assertFalse(sources.get(1).has("grantee"));
    assertEquals(12, sources.get(2).get("grantee").get("id").asLong());
    assertEquals("ALL_USERS", sources.get(3).get("grantee").get("id").asText());
    for (JsonNode source : sources) {
      assertFalse(source.has("granteeKey"));
      assertFalse(source.has("nameSnapshot"));
    }
    assertEquals(4, sources.get(2).get("grantee").size());
  }

  @Test
  void inheritedRoleProjectionKeepsOwnerHealthRestrictedToSysadmins() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.prepareAccessProjection(
        "OWNER", List.of(ResourceRoleSource.implicit("OWNER")), null, null);
    User owner = mock(User.class);
    User sysadmin = mock(User.class);
    when(sysadmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    ResourceRenderer renderer = new ResourceRenderer(REGISTRY);
    var selection =
        ResourceFieldSelections.root(FieldSelection.include(Set.of("roleSources", "ownerHealth")));

    Map<String, Object> ownerDocument =
        renderer.render(
            configuration,
            ApiV2BookingConfigurationResource.DESCRIPTION,
            selection,
            IncludeTree.empty(),
            (resourceName, id) -> Optional.empty(),
            Map.of(),
            new AccessContext(owner, Operation.READ, "booking-configurations"));
    assertEquals(
        List.of(Map.of("kind", "IMPLICIT", "role", "OWNER")), ownerDocument.get("roleSources"));
    assertFalse(ownerDocument.containsKey("ownerHealth"));

    Map<String, Object> sysadminDocument =
        renderer.render(
            configuration,
            ApiV2BookingConfigurationResource.DESCRIPTION,
            selection,
            IncludeTree.empty(),
            (resourceName, id) -> Optional.empty(),
            Map.of(),
            new AccessContext(sysadmin, Operation.READ, "booking-configurations"));
    assertEquals(Map.of(), sysadminDocument.get("ownerHealth"));
  }

  private DocumentValidationException updateViolation(JsonNode body) {
    return assertThrows(DocumentValidationException.class, () -> parseUpdate(body));
  }

  private ParsedDocument parseUpdate(JsonNode body) {
    return ApiV2DocumentParser.parse(
        body,
        ApiV2BookingConfigurationResource.DESCRIPTION,
        WriteOperation.UPDATE,
        "errors.api.v2.bookingConfiguration.patch",
        new AccessContext(null, Operation.UPDATE, "booking-configurations", 42L));
  }

  @Test
  void parsesCompleteReplacementRowsAndSortsOnApplication() throws Exception {
    ParsedDocument document =
        parseUpdate(
            mapper.readTree(
                """
                {"openingHours":[{"dayOfWeek":6,"start":"10:00","end":"16:00"},
                                 {"dayOfWeek":1,"start":"00:00","end":"24:00"}]}
                """));
    BookingConfiguration configuration = new BookingConfiguration();
    ApiV2BookingConfigurationResource.DESCRIPTION.apply(
        configuration, document.values(), WriteOperation.UPDATE);
    assertEquals(
        List.of(
            new BookingOpeningHoursEntry(1, "00:00", "24:00"),
            new BookingOpeningHoursEntry(6, "10:00", "16:00")),
        configuration.getOpeningHours());
  }
}
