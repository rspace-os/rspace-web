package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.resource.ApiV2DocumentParser;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.DocumentValidationException;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.model.collection.WriteOperation;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApiV2TimeSlotBookingResourceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void definesTheFirstSliceWireAndQueryContract() {
    assertEquals(
        List.of("kind", "start", "end", "purpose", "target"),
        List.copyOf(
            ApiV2TimeSlotBookingResource.DESCRIPTION.writableFields(WriteOperation.CREATE)));
    assertEquals(
        List.of("start", "end", "state", "purpose"),
        List.copyOf(
            ApiV2TimeSlotBookingResource.DESCRIPTION.writableFields(WriteOperation.UPDATE)));
    for (String privateField :
        List.of("bookedBy", "createdBy", "privacy", "canEdit", "canViewConfiguration")) {
      assertFalse(ApiV2TimeSlotBookingResource.DESCRIPTION.requireField(privateField).sortable());
      assertFalse(
          ApiV2TimeSlotBookingResource.DESCRIPTION
              .requireField(privateField)
              .operators()
              .iterator()
              .hasNext());
    }
    assertFalse(ApiV2TimeSlotBookingResource.DESCRIPTION.requireField("purpose").sortable());
    new RsqlFilterParser(ApiV2TimeSlotBookingResource.DESCRIPTION).parse("purpose=contains=secret");

    var requesterId = ApiV2TimeSlotBookingResource.DESCRIPTION.requireField("requesterId");
    assertFalse(requesterId.sortable());
    assertEquals(CollectionFieldTypes.longNumber().operators(), requesterId.operators());
    new RsqlFilterParser(ApiV2TimeSlotBookingResource.DESCRIPTION).parse("requesterId==42");

    var calendar =
        ApiV2TimeSlotBookingResource.calendarFilterDescription(
            com.researchspace.model.collection.AccessFunction.authenticated());
    new RsqlFilterParser(calendar).parse("timezone==UTC");
    new RsqlFilterParser(calendar)
        .parse(
            "purpose=contains=scope;bookedBy=contains=ada;privacy==full;"
                + "kind==BOOKING;start=ge=2026-10-25T00:30:00Z;end=le=2026-10-25T02:30:00Z");
  }

  @Test
  void parsesOnlyDocumentedCreateAndUpdateFields() throws Exception {
    var create =
        ApiV2DocumentParser.parse(
            mapper.readTree(
                """
                {
                  "target": {"relationTo": "booking-instruments", "value": 12},
                  "start": "2026-10-25T00:30:00Z",
                  "end": "2026-10-25T02:30:00Z",
                  "kind": "MAINTENANCE",
                  "purpose": "Plate 4"
                }
                """),
            ApiV2TimeSlotBookingResource.DESCRIPTION,
            WriteOperation.CREATE,
            "errors.api.v2.booking.create",
            new AccessContext(null, Operation.CREATE, "bookings"));

    assertEquals(
        new com.researchspace.model.collection.ResourceReference<>(
            BookableTargetType.INSTRUMENT, 12L),
        create.values().get("target"));
    assertEquals(BookingEventKind.MAINTENANCE, create.values().get("kind"));
    assertThrows(
        DocumentValidationException.class,
        () ->
            ApiV2DocumentParser.parse(
                mapper.readTree(
                    """
                    {"target":{"relationTo":"booking-instruments","value":12},
                     "start":"2026-10-25T00:30:00Z","end":"2026-10-25T02:30:00Z",
                     "bookedBy":"forged"}
                    """),
                ApiV2TimeSlotBookingResource.DESCRIPTION,
                WriteOperation.CREATE,
                "errors.api.v2.booking.create",
                new AccessContext(null, Operation.CREATE, "bookings")));
    assertThrows(
        DocumentValidationException.class,
        () ->
            ApiV2DocumentParser.parse(
                mapper.readTree("{\"target\":\"IN13\"}"),
                ApiV2TimeSlotBookingResource.DESCRIPTION,
                WriteOperation.UPDATE,
                "errors.api.v2.booking.patch",
                new AccessContext(null, Operation.UPDATE, "bookings", 41L)));
    assertThrows(
        DocumentValidationException.class,
        () ->
            ApiV2DocumentParser.parse(
                mapper.readTree("{\"kind\":\"MAINTENANCE\"}"),
                ApiV2TimeSlotBookingResource.DESCRIPTION,
                WriteOperation.UPDATE,
                "errors.api.v2.booking.patch",
                new AccessContext(null, Operation.UPDATE, "bookings", 41L)));
  }

  @Test
  void rendersMixedFullAndBusyValuesWithoutChangingPersistedDetail() {
    User requester = new User("ada");
    requester.setFirstName("Ada");
    requester.setLastName("Lovelace");
    TimeSlotBooking booking = booking(requester);
    booking.setCreatedBy(requester);

    Map<String, Object> busy = ApiV2TimeSlotBookingResource.DESCRIPTION.toDocument(booking);
    assertEquals("busy", busy.get("privacy"));
    assertNull(busy.get("purpose"));
    assertNull(busy.get("bookedBy"));
    assertEquals(false, busy.get("canViewConfiguration"));
    assertEquals("Secret", booking.getPurpose());

    booking.prepareView(BookingPrivacy.FULL, true, true);
    Map<String, Object> full = ApiV2TimeSlotBookingResource.DESCRIPTION.toDocument(booking);
    assertEquals("full", full.get("privacy"));
    assertEquals("Secret", full.get("purpose"));
    assertEquals("Ada Lovelace (ada)", full.get("bookedBy"));
    assertEquals("Ada Lovelace (ada)", full.get("createdBy"));
    assertEquals("BOOKING", full.get("kind"));
    assertEquals(true, full.get("canEdit"));
    assertEquals(true, full.get("canViewConfiguration"));
  }

  @Test
  void redactsTheTargetAndConfigurationTimezoneWhenTheRequesterLostItemAccess() {
    User requester = new User("ada");
    TimeSlotBooking booking = booking(requester);
    var target = ApiV2TimeSlotBookingResource.DESCRIPTION.requireRelationship("target");

    booking.prepareView(BookingPrivacy.FULL, false, false);

    assertNull(booking.getVisibleTarget());
    assertNull(booking.getVisibleTimeZone());
    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L),
        booking.getBookingConfiguration().getTarget());
    assertTrue(
        ApiV2TimeSlotBookingResource.DESCRIPTION.relationshipTargetId(booking, target).isEmpty());

    booking.prepareView(BookingPrivacy.FULL, false, true);

    assertEquals(
        Optional.of(12L),
        ApiV2TimeSlotBookingResource.DESCRIPTION.relationshipTargetId(booking, target));
    assertEquals("Europe/Berlin", booking.getVisibleTimeZone());
  }

  private static TimeSlotBooking booking(User requester) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("Europe/Berlin");
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L));
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(41L);
    booking.setBookingConfiguration(configuration);
    booking.setRequester(requester);
    booking.setStartTime(Date.from(Instant.parse("2026-10-25T00:30:00Z")));
    booking.setEndTime(Date.from(Instant.parse("2026-10-25T02:30:00Z")));
    booking.setState(BookingState.CONFIRMED);
    booking.setPurpose("Secret");
    return booking;
  }
}
