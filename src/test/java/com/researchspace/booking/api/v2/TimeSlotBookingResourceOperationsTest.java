package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.featureflags.FeatureFlags;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import java.time.Instant;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimeSlotBookingResourceOperationsTest {

  private final TimeSlotBookingManager manager = mock(TimeSlotBookingManager.class);
  private final FeatureFlagManager featureFlags = mock(FeatureFlagManager.class);
  private final TimeSlotBookingResourceOperations operations =
      new TimeSlotBookingResourceOperations(
          manager, featureFlags, ApiV2TimeSlotBookingResource.DESCRIPTION);
  private final User actor = mock(User.class);

  @BeforeEach
  void enableBooking() {
    when(featureFlags.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, actor)).thenReturn(true);
  }

  @Test
  void exposesOnlyTheFirstSliceOperations() {
    ApiV2ResourceSpec<TimeSlotBooking, Long> spec = operations.timeSlotBookingApiV2Resource();

    assertEquals(
        EnumSet.of(
            ResourceOperation.LIST,
            ResourceOperation.COUNT,
            ResourceOperation.READ,
            ResourceOperation.CREATE,
            ResourceOperation.UPDATE),
        spec.exposedOperations());
  }

  @Test
  void translatesCreateAndPresenceAwarePatchCommands() {
    ResolvedResourceReference<BookableTargetType, Long> target = resolved(12L);
    ParsedDocument create =
        new ParsedDocument(
            WriteOperation.CREATE,
            Map.of(
                "target",
                target,
                "start",
                start(),
                "end",
                end(),
                "purpose",
                "Image plate 4",
                "kind",
                BookingEventKind.MAINTENANCE));
    TimeSlotBooking booking = new TimeSlotBooking();
    TimeSlotBookingManager.Create command =
        new TimeSlotBookingManager.Create(
            new com.researchspace.model.booking.ResolvedBookableTarget(
                new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L),
                target.entityAs(Instrument.class)),
            start(),
            end(),
            "Image plate 4",
            BookingEventKind.MAINTENANCE);
    when(manager.createBooking(command, actor, actor)).thenReturn(booking);

    assertEquals(booking, operations.create(create, ApiV2Caller.direct(actor)));
    verify(manager).createBooking(command, actor, actor);

    ParsedDocument patch = ParsedDocument.update(Map.of("purpose", "", "end", end()));
    operations.update(41L, patch, ApiV2Caller.direct(actor));
    verify(manager)
        .updateBooking(
            41L, new TimeSlotBookingManager.Patch(null, end(), true, "", null), actor, actor);
  }

  @Test
  void derivedPrivateFieldsAreNullableAndNeverQueryable() {
    User requester = mock(User.class);
    TimeSlotBooking booking = booking(requester);

    Map<String, Object> busy = ApiV2TimeSlotBookingResource.DESCRIPTION.toDocument(booking);

    assertEquals(null, busy.get("purpose"));
    assertEquals(null, busy.get("bookedBy"));
    assertEquals("busy", busy.get("privacy"));
    assertEquals(false, busy.get("canEdit"));
    assertFalse(ApiV2TimeSlotBookingResource.DESCRIPTION.requireField("purpose").sortable());
    assertFalse(
        ApiV2TimeSlotBookingResource.DESCRIPTION
            .requireField("purpose")
            .operators()
            .iterator()
            .hasNext());
    assertThrows(
        CollectionQueryException.class,
        () ->
            ApiV2TimeSlotBookingResource.DESCRIPTION.requireWritableField(
                "state", WriteOperation.CREATE));
    assertFalse(
        ApiV2TimeSlotBookingResource.DESCRIPTION
            .requireRelationship("target")
            .writableOn(WriteOperation.UPDATE));
  }

  @Test
  void featureFlagSuppressesReadsAndRefusesWrites() {
    when(featureFlags.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, actor)).thenReturn(false);
    ResourceRequest request = ResourceRequest.unpaged(null);

    assertEquals(0, operations.find(request, actor).total());
    assertEquals(0, operations.count(request, actor));
    assertEquals(Optional.empty(), operations.findById(1L, actor));
    assertThrows(
        AuthorizationException.class,
        () ->
            operations.create(
                new ParsedDocument(WriteOperation.CREATE, Map.of()), ApiV2Caller.direct(actor)));
    assertEquals(
        Optional.empty(),
        operations.update(1L, ParsedDocument.update(Map.of()), ApiV2Caller.direct(actor)));
    verifyNoInteractions(manager);
  }

  private static ResolvedResourceReference<BookableTargetType, Long> resolved(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return new ResolvedResourceReference<>(
        new ResourceReference<>(BookableTargetType.INSTRUMENT, id), instrument);
  }

  private static TimeSlotBooking booking(User requester) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("Europe/Berlin");
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L));
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(41L);
    booking.setBookingConfiguration(configuration);
    booking.setRequester(requester);
    booking.setStartTime(start());
    booking.setEndTime(end());
    booking.setState(BookingState.CONFIRMED);
    booking.setPurpose("Secret");
    booking.setCreatedAt(start());
    booking.setUpdatedAt(start());
    return booking;
  }

  private static Date start() {
    return Date.from(Instant.parse("2026-10-25T07:30:00Z"));
  }

  private static Date end() {
    return Date.from(Instant.parse("2026-10-25T09:00:00Z"));
  }
}
