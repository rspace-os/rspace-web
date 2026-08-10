package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.resource.ApiV2DocumentParser;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterSelector;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipInputForm;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRenderer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiV2BookingConfigurationResourceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void usesResourceSpecificAuditIdentifier() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(7L);

    assertEquals("booking-configurations:7", configuration.getAuditTrailIdentifier());
  }

  @Test
  void definesScalarFieldsAndTheWritableTargetRelationship() {
    assertEquals(
        List.of("id", "enabled", "timezone", "configurationVersion", "createdAt", "updatedAt"),
        ApiV2BookingConfigurationResource.DESCRIPTION.fields().stream()
            .map(field -> field.name())
            .toList());
    assertEquals(
        List.of("enabled", "timezone", "target"),
        List.copyOf(
            ApiV2BookingConfigurationResource.DESCRIPTION.writableFields(WriteOperation.UPDATE)));
    assertEquals(
        List.of("target", "createdBy", "updatedBy"),
        ApiV2BookingConfigurationResource.DESCRIPTION.relationships().stream()
            .map(relationship -> relationship.name())
            .toList());
    assertInstanceOf(
        FilterSelector.Property.class,
        ApiV2BookingConfigurationResource.DESCRIPTION.requireFilterSelector("createdAt"));
    assertInstanceOf(
        FilterSelector.Property.class,
        ApiV2BookingConfigurationResource.DESCRIPTION.requireFilterSelector("updatedAt"));
    FilterSelector.Property timezone =
        assertInstanceOf(
            FilterSelector.Property.class,
            ApiV2BookingConfigurationResource.DESCRIPTION.requireFilterSelector("timezone"));
    assertEquals("timeZone", timezone.property());
    assertInstanceOf(
        FilterSelector.RelationshipPart.class,
        ApiV2BookingConfigurationResource.DESCRIPTION.requireFilterSelector("createdBy.value"));
    assertInstanceOf(
        FilterSelector.RelationshipPart.class,
        ApiV2BookingConfigurationResource.DESCRIPTION.requireFilterSelector("updatedBy.value"));
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .requireRelationship("target")
            .acceptsInput(WriteOperation.UPDATE, RelationshipInputForm.GLOBAL_ID));
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION.requireRelationship("createdBy").nullable());
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION.requireRelationship("updatedBy").nullable());
  }

  @Test
  void declaresAuthenticatedReadsAndSysadminWrites() {
    User member = mock(User.class);
    User sysadmin = mock(User.class);
    when(sysadmin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);

    assertTrue(
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
                  "timezone": "Europe/Berlin",
                  "target": {"relationTo": "instruments", "value": 12}
                }
                """),
            ApiV2BookingConfigurationResource.DESCRIPTION,
            WriteOperation.CREATE,
            "errors.api.v2.bookingConfiguration.create",
            new AccessContext(null, Operation.CREATE, "booking-configurations"));
    BookingConfiguration configuration = new BookingConfiguration();

    ApiV2BookingConfigurationResource.DESCRIPTION.apply(
        configuration, Map.of("enabled", true, "timezone", "Europe/Berlin"), WriteOperation.CREATE);

    assertTrue(configuration.isEnabled());
    assertEquals("Europe/Berlin", configuration.getTimeZone());
    assertTrue(configuration.isTimeZoneValid());
    Map<String, Object> rendered =
        ApiV2BookingConfigurationResource.DESCRIPTION.toDocument(configuration);
    assertEquals(
        List.of("id", "enabled", "timezone", "configurationVersion", "createdAt", "updatedAt"),
        List.copyOf(rendered.keySet()));
    assertNull(rendered.get("id"));
    assertEquals(true, rendered.get("enabled"));
    assertEquals("Europe/Berlin", rendered.get("timezone"));
    assertEquals(0L, rendered.get("configurationVersion"));
    assertNull(rendered.get("createdAt"));
    assertNull(rendered.get("updatedAt"));
    assertEquals(
        new ResourceReference<>(BookableTargetType.INSTRUMENT, 12L),
        document.values().get("target"));
  }

  @Test
  void rendersAuditUsersAsReadOnlyUserRelationships() {
    User creator = mock(User.class);
    User updater = mock(User.class);
    when(creator.getId()).thenReturn(21L);
    when(updater.getId()).thenReturn(22L);
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setCreatedBy(creator);
    configuration.setUpdatedBy(updater);
    ResourceRegistry registry =
        new ResourceRegistry(
            List.of(
                ApiV2BookingConfigurationResource.DESCRIPTION,
                ApiV2UserResource.DESCRIPTION,
                ApiV2InstrumentResource.DESCRIPTION));

    Map<String, Object> rendered =
        new ResourceRenderer(registry)
            .render(
                configuration,
                ApiV2BookingConfigurationResource.DESCRIPTION,
                FieldSelection.all(),
                IncludeTree.empty());

    assertEquals(Map.of("relationTo", "users", "value", 21L), rendered.get("createdBy"));
    assertEquals(Map.of("relationTo", "users", "value", 22L), rendered.get("updatedBy"));
    assertFalse(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .writableFields(WriteOperation.CREATE)
            .contains("createdBy"));
  }

  @Test
  void requiresATimeZoneAndRecognizesInvalidZoneIds() throws Exception {
    assertThrows(
        DocumentValidationException.class,
        () ->
            ApiV2DocumentParser.parse(
                mapper.readTree(
                    "{\"enabled\":true,\"target\":{\"relationTo\":\"instruments\",\"value\":12}}"),
                ApiV2BookingConfigurationResource.DESCRIPTION,
                WriteOperation.CREATE,
                "errors.api.v2.bookingConfiguration.create",
                new AccessContext(null, Operation.CREATE, "booking-configurations")));

    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("Not/A_Zone");
    assertFalse(configuration.isTimeZoneValid());
  }

  @Test
  void acceptsOnlyInstrumentGlobalIdsForTargetPatches() throws Exception {
    ParsedDocument document =
        ApiV2DocumentParser.parse(
            mapper.readTree("{\"target\":\"IN12\"}"),
            ApiV2BookingConfigurationResource.DESCRIPTION,
            WriteOperation.UPDATE,
            "errors.api.v2.bookingConfiguration.patch",
            new AccessContext(null, Operation.UPDATE, "booking-configurations", 42L));

    assertEquals(
        new ResourceReference<>(BookableTargetType.INSTRUMENT, 12L),
        document.values().get("target"));
    assertThrows(
        DocumentValidationException.class,
        () ->
            ApiV2DocumentParser.parse(
                mapper.readTree("{\"target\":\"SA12\"}"),
                ApiV2BookingConfigurationResource.DESCRIPTION,
                WriteOperation.UPDATE,
                "errors.api.v2.bookingConfiguration.patch",
                new AccessContext(null, Operation.UPDATE, "booking-configurations", 42L)));
  }
}
