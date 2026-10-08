package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.model.ApiV2AuditEvent;
import com.researchspace.api.v2.model.ApiV2AuditPage;
import com.researchspace.api.v2.model.ApiV2AuditQuery;
import com.researchspace.api.v2.resource.ApiV2AuditLog;
import com.researchspace.api.v2.resource.ApiV2AuditStrictSearch;
import com.researchspace.api.v2.resource.ApiV2RelationshipTargetSpec;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceException;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.booking.service.BookingBufferConflictException;
import com.researchspace.booking.service.BookingConcurrentModificationException;
import com.researchspace.booking.service.BookingOverlapException;
import com.researchspace.booking.service.ConflictingEvent;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.featureflags.FeatureFlags;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionQueryException;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResolvedResourceReference;
import com.researchspace.model.collection.ResourceReference;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.WriteOperation;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.audit.search.AuditTrailActorVisibility;
import com.researchspace.testutils.TestFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

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
  void limitsRequesterFiltersToEventsWhoseRequesterTheSubjectMaySee() {
    when(actor.getId()).thenReturn(7L);
    ResourceRequest request =
        new ResourceRequest(
            new com.researchspace.model.collection.FilterExpression.Comparison(
                "requesterId",
                com.researchspace.model.collection.Operator.EQUAL,
                List.of(42L),
                false),
            List.of(),
            new ResourceRequest.Page(1, 20),
            com.researchspace.model.collection.FieldSelection.all(),
            com.researchspace.model.collection.IncludeTree.empty());
    var guarded =
        com.researchspace.model.booking.BookingRequesterFilters.visibleRequestersOnly(
            request.filter(), 7L);
    when(manager.getBookings(any(ResourceRequest.class), eq(actor)))
        .thenReturn(new com.researchspace.model.collection.ResourcePage<>(List.of(), 0));
    when(manager.countBookings(any(ResourceRequest.class), eq(actor))).thenReturn(0L);

    operations.find(request, actor);
    operations.count(request, actor);

    var captured = org.mockito.ArgumentCaptor.forClass(ResourceRequest.class);
    verify(manager).getBookings(captured.capture(), eq(actor));
    assertEquals(guarded, captured.getValue().filter());
    verify(manager).countBookings(captured.capture(), eq(actor));
    assertEquals(guarded, captured.getValue().filter());
  }

  @Test
  void mapsSchedulingConflictsForBothCreateAndUpdate() {
    var spec = operations.timeSlotBookingApiV2Resource();
    Map.of(
            ResourceOperation.CREATE, HttpStatus.CONFLICT,
            ResourceOperation.UPDATE, HttpStatus.PRECONDITION_FAILED)
        .forEach(
            (operation, status) -> {
              var mapping =
                  spec.errorMappings().get(operation).stream()
                      .filter(
                          error ->
                              error.exceptionType() == BookingConcurrentModificationException.class)
                      .findFirst()
                      .orElseThrow();
              assertEquals(
                  status, mapping.translate(new BookingConcurrentModificationException()).status());
            });
  }

  @Test
  void overlapAndBufferConflictsCarryOnlyTheBlockingEventsPublicInterval() {
    var spec = operations.timeSlotBookingApiV2Resource();
    ConflictingEvent blocking =
        new ConflictingEvent(
            59L,
            BookingEventKind.MAINTENANCE,
            Instant.parse("2026-09-28T08:00:00Z"),
            Instant.parse("2026-09-28T10:00:00Z"));
    ApiV2Problem.BookingConflict expected =
        new ApiV2Problem.BookingConflict(
            59L, "MAINTENANCE", "2026-09-28T08:00:00Z", "2026-09-28T10:00:00Z");
    for (ResourceOperation operation :
        EnumSet.of(ResourceOperation.CREATE, ResourceOperation.UPDATE)) {
      ApiV2ResourceException overlap =
          translate(spec, operation, new BookingOverlapException(blocking));
      assertEquals(HttpStatus.CONFLICT, overlap.status());
      assertEquals("errors.api.v2.booking.overlap", overlap.errorCode());
      assertEquals(ApiV2Problem.Extensions.bookingConflict(expected), overlap.extensions());

      ApiV2ResourceException buffer =
          translate(spec, operation, new BookingBufferConflictException(blocking, 15, 30));
      assertEquals(HttpStatus.CONFLICT, buffer.status());
      assertEquals("errors.api.v2.booking.buffer", buffer.errorCode());
      assertEquals(ApiV2Problem.Extensions.bookingBuffer(expected, 15, 30), buffer.extensions());
    }
  }

  private static ApiV2ResourceException translate(
      ApiV2ResourceSpec<TimeSlotBooking, Long> spec,
      ResourceOperation operation,
      RuntimeException failure) {
    return spec.errorMappings().get(operation).stream()
        .filter(error -> error.exceptionType() == failure.getClass())
        .findFirst()
        .orElseThrow()
        .translate(failure);
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
  void derivedPrivateFieldsAreNullableAndUnsortable() {
    User requester = mock(User.class);
    TimeSlotBooking booking = booking(requester);

    Map<String, Object> busy = ApiV2TimeSlotBookingResource.DESCRIPTION.toDocument(booking);

    assertEquals(null, busy.get("purpose"));
    assertEquals(null, busy.get("bookedBy"));
    assertEquals("busy", busy.get("privacy"));
    assertEquals(false, busy.get("canEdit"));
    assertFalse(ApiV2TimeSlotBookingResource.DESCRIPTION.requireField("purpose").sortable());
    assertTrue(
        ApiV2TimeSlotBookingResource.DESCRIPTION
            .requireField("purpose")
            .operators()
            .contains(com.researchspace.model.collection.Operator.CONTAINS));
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
    assertThrows(
        AuthorizationException.class,
        () -> operations.update(1L, ParsedDocument.update(Map.of()), ApiV2Caller.direct(actor)));
    verifyNoInteractions(manager);
  }

  /**
   * A bookable item manager with the audit capability often shares no group with the people who
   * book the item, so none of the booking's own events are by users in their directory.
   */
  @Test
  void bookingAuditIncludesEventsByUsersOutsideTheViewersDirectory(@TempDir Path logs)
      throws Exception {
    User itemManager = TestFactory.createAnyUser("item-manager");
    User booker = TestFactory.createAnyUser("outside-booker");
    when(featureFlags.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, itemManager))
        .thenReturn(true);
    when(manager.getBookingForAudit(41L, itemManager)).thenReturn(Optional.of(booking(booker)));
    UserManager users = mock(UserManager.class);
    when(users.getViewableUsers(eq(itemManager), any()))
        .thenReturn(new SearchResultsImpl<User>(List.of(), 0, 0L));
    Instant created = Instant.parse("2026-09-23T12:00:00Z");
    Files.writeString(
        logs.resolve("RSLogs.txt"),
        DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm:ss,SSS", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault())
                .format(created)
            + " - domain:BOOKING action:CREATE [{\"data\":{\"id\":\"bookings:41\","
            + "\"bookingConfigurationId\":\"booking-configurations:12\"}}]"
            + " outside-booker(Outside Booker)\n");
    ApiV2AuditLog auditLog =
        new ApiV2AuditLog(
            new ApiV2AuditStrictSearch(logs.toString(), new AuditTrailActorVisibility(users)),
            Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC),
            100);
    ApiV2ResourceRegistration<?, ?> bookings =
        new ApiV2ResourceCatalog(
                List.of(operations.timeSlotBookingApiV2Resource()),
                List.of(
                    new ApiV2RelationshipTargetSpec<>(
                        ApiV2InstrumentResource.DESCRIPTION, Long.class, (ids, actor) -> Map.of()),
                    new ApiV2RelationshipTargetSpec<>(
                        ApiV2BookingInstrumentResource.DESCRIPTION,
                        Long.class,
                        (ids, actor) -> Map.of()),
                    new ApiV2RelationshipTargetSpec<>(
                        ApiV2UserResource.DESCRIPTION, Long.class, (ids, actor) -> Map.of())))
            .find("bookings")
            .orElseThrow();

    ApiV2AuditPage<ApiV2AuditEvent> page =
        auditLog.search(bookings, "41", new ApiV2AuditQuery(), itemManager);

    assertEquals(1, page.totalDocs());
    verify(manager).requireCanViewAudit(any(TimeSlotBooking.class), eq(itemManager));
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
