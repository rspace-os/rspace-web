package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.resource.ApiV2DocumentParser;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.RelationshipInputForm;
import com.researchspace.model.collection.ResourceReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiV2BookingConfigurationResourceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void definesScalarFieldsAndTheWritableTargetRelationship() {
    assertEquals(
        List.of("id", "enabled", "timeZone", "configurationVersion"),
        ApiV2BookingConfigurationResource.DESCRIPTION.fields().stream()
            .map(field -> field.name())
            .toList());
    assertEquals(
        List.of("enabled", "timeZone", "target"),
        List.copyOf(
            ApiV2BookingConfigurationResource.DESCRIPTION.writableFields(WriteOperation.UPDATE)));
    assertEquals(
        List.of("target"),
        ApiV2BookingConfigurationResource.DESCRIPTION.relationships().stream()
            .map(relationship -> relationship.name())
            .toList());
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .requireRelationship("target")
            .acceptsInput(WriteOperation.UPDATE, RelationshipInputForm.GLOBAL_ID));
  }

  @Test
  void declaresAuthenticatedReadsAndSysadminWrites() {
    User member = mock(User.class);
    User sysadmin = mock(User.class);
    when(sysadmin.hasRole(com.researchspace.model.Role.SYSTEM_ROLE)).thenReturn(true);

    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .accessPolicy()
            .update()
            .check(new AccessContext(member, Operation.UPDATE, "booking-configurations", 42L))
            .isDenied());
    assertFalse(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .accessPolicy()
            .update()
            .check(new AccessContext(sysadmin, Operation.UPDATE, "booking-configurations", 42L))
            .isDenied());
    assertTrue(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .accessPolicy()
            .update()
            .check(new AccessContext(null, Operation.UPDATE, "booking-configurations", 42L))
            .isDenied());
    assertFalse(
        ApiV2BookingConfigurationResource.DESCRIPTION
            .accessPolicy()
            .read()
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
                  "timeZone": "Europe/Berlin",
                  "target": {"relationTo": "instruments", "value": 12}
                }
                """),
            ApiV2BookingConfigurationResource.DESCRIPTION,
            WriteOperation.CREATE,
            "errors.api.v2.bookingConfiguration.create",
            new AccessContext(null, Operation.CREATE, "booking-configurations"));
    BookingConfiguration configuration = new BookingConfiguration();

    ApiV2BookingConfigurationResource.DESCRIPTION.apply(
        configuration, Map.of("enabled", true, "timeZone", "Europe/Berlin"), WriteOperation.CREATE);

    assertTrue(configuration.isEnabled());
    assertEquals("Europe/Berlin", configuration.getTimeZone());
    assertTrue(configuration.isTimeZoneValid());
    Map<String, Object> rendered =
        ApiV2BookingConfigurationResource.DESCRIPTION.toDocument(configuration);
    assertEquals(
        List.of("id", "enabled", "timeZone", "configurationVersion"),
        List.copyOf(rendered.keySet()));
    assertNull(rendered.get("id"));
    assertEquals(true, rendered.get("enabled"));
    assertEquals("Europe/Berlin", rendered.get("timeZone"));
    assertEquals(0L, rendered.get("configurationVersion"));
    assertEquals(
        new ResourceReference<>(BookableTargetType.INSTRUMENT, 12L),
        document.values().get("target"));
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
