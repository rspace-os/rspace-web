package com.researchspace.booking.service;

import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class TimeSlotBookingManagerTest {

  private static final Set<BookingEventKind> CONFLICTING_EVENT_KINDS =
      Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE);

  private final TimeSlotBookingDao bookingDao = mock(TimeSlotBookingDao.class);
  private final BookingConfigurationDao configurationDao = mock(BookingConfigurationDao.class);
  private final InstrumentDao instrumentDao = mock(InstrumentDao.class);
  private final ObjectProvider<ResourceRegistry> registry = mock(ObjectProvider.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final BookingSchedulingPolicy schedulingPolicy = new BookingSchedulingPolicy();
  private final User actor = mock(User.class);
  private final BookingItemPermissions accessManager = mock(BookingItemPermissions.class);
  private final BookingNotificationService bookingNotificationService =
      mock(BookingNotificationService.class);
  private final TimeSlotBookingManager manager =
      new TimeSlotBookingManagerImpl(
          bookingDao,
          configurationDao,
          schedulingPolicy,
          instrumentDao,
          bookingNotificationService,
          registry,
          events,
          accessManager,
          ApiV2TimeSlotBookingResource.DESCRIPTION,
          ApiV2BookingConfigurationResource.DESCRIPTION,
          Clock.fixed(Instant.parse("2025-12-01T00:00:00Z"), ZoneOffset.UTC));

  @BeforeEach
  void setUp() {
    when(actor.getId()).thenReturn(1L);
    when(actor.getUsername()).thenReturn("ada");
    when(actor.getFullName()).thenReturn("Ada Lovelace");
    when(actor.isEnabled()).thenReturn(true);
    when(registry.getObject())
        .thenReturn(
            new ResourceRegistry(
                List.of(
                    ApiV2TimeSlotBookingResource.DESCRIPTION,
                    ApiV2BookingConfigurationResource.DESCRIPTION,
                    ApiV2BookingInstrumentResource.DESCRIPTION,
                    ApiV2InstrumentResource.DESCRIPTION,
                    ApiV2UserResource.DESCRIPTION)));
    when(bookingDao.saveAndFlush(any(TimeSlotBooking.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(accessManager.resolveForMutation(any(BookingConfiguration.class), eq(actor)))
        .thenReturn(ownerAccess());
    when(accessManager.resolve(any(BookingConfiguration.class), eq(actor)))
        .thenReturn(ownerAccess());
    when(accessManager.resolveAll(any(), eq(actor)))
        .thenAnswer(
            invocation -> {
              Collection<BookingConfiguration> accesses = invocation.getArgument(0);
              return accesses.stream()
                  .collect(Collectors.toMap(BookingConfiguration::getId, ignored -> ownerAccess()));
            });
  }

  @Test
  void calendarEventFactoryCopiesThePreparedPrivacyFieldsAndItemName() {
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(41L);
    booking.setStartTime(start());
    booking.setEndTime(end());
    booking.setCreatedAt(start());
    booking.setUpdatedAt(end());
    booking.setKind(BookingEventKind.MAINTENANCE);
    booking.setRequester(actor);
    booking.setCreatedBy(actor);
    booking.setPurpose("Private purpose");
    booking.prepareView(BookingPrivacy.BUSY, true, true);

    TimeSlotBookingManager.CalendarEvent event =
        TimeSlotBookingManager.CalendarEvent.of(booking, "Microscope");

    assertEquals(41L, event.id());
    assertEquals(start(), event.start());
    assertEquals(end(), event.end());
    assertEquals(start(), event.createdAt());
    assertEquals(end(), event.updatedAt());
    assertEquals(BookingEventKind.MAINTENANCE, event.kind());
    assertEquals(BookingPrivacy.BUSY, event.privacy());
    assertNull(event.bookedBy());
    assertEquals("Ada Lovelace (ada)", event.createdBy());
    assertNull(event.purpose());
    assertFalse(event.canEdit());
    assertEquals("Microscope", event.itemName());
  }

  @Test
  void calendarRequestValidationAndLimitPrecedePreparation() {
    Date refreshedAt = instant("2026-10-01T00:00:00Z");

    assertThrows(
        IllegalArgumentException.class, () -> manager.getCalendarSource(4L, actor, refreshedAt, 0));
    assertThrows(
        IllegalArgumentException.class, () -> manager.getUserCalendarSource(actor, refreshedAt, 0));

    verifyNoInteractions(bookingDao, configurationDao, instrumentDao);
    when(bookingDao.findUserCalendarBookings(eq(actor.getId()), any(Date.class), eq(2)))
        .thenReturn(List.of(new TimeSlotBooking(), new TimeSlotBooking()));

    assertThrows(
        TimeSlotBookingManager.CalendarSourceTooLargeException.class,
        () -> manager.getUserCalendarSource(actor, refreshedAt, 1));

    ArgumentCaptor<Date> cutoff = ArgumentCaptor.forClass(Date.class);
    verify(bookingDao).findUserCalendarBookings(eq(actor.getId()), cutoff.capture(), eq(2));
    assertEquals(instant("2026-09-01T00:00:00Z"), cutoff.getValue());
    verify(accessManager, never()).resolveAll(any(), eq(actor));
  }

  @Test
  void createsInsideTheConfigurationLockAndPreparesAFullResponse() {
    BookingConfiguration configuration = configuration(4L, 12L, true);
    ResolvedBookableTarget target = target(12L);
    when(configurationDao.lockActiveByTarget(target.reference()))
        .thenReturn(Optional.of(configuration));
    when(bookingDao.findFirstOverlap(
            4L,
            start(),
            end(),
            null,
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE)))
        .thenReturn(Optional.empty());

    TimeSlotBooking created =
        manager.createBooking(
            new TimeSlotBookingManager.Create(target, start(), end(), "Image plate 4"),
            actor,
            actor);

    assertSame(configuration, created.getBookingConfiguration());
    assertSame(actor, created.getRequester());
    assertSame(actor, created.getCreatedBy());
    assertEquals(BookingEventKind.BOOKING, created.getKind());
    assertEquals(BookingState.CONFIRMED, created.getState());
    assertEquals(BookingPrivacy.FULL, created.getPrivacy());
    assertTrue(created.isCanEdit());
    assertFalse(created.isDeleted());
    verify(configurationDao).lockActiveByTarget(target.reference());
    verify(bookingDao)
        .findFirstOverlap(
            4L,
            start(),
            end(),
            null,
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE));
    verify(events).publishEvent(any(TimeSlotBookingAuditEvent.class));
    verify(bookingNotificationService)
        .notify(created, actor, NotificationType.NOTIFICATION_BOOKING_CREATED);
  }

  @Test
  void rejectsPastAndCurrentStartsBeforeSavingOrPublishing() {
    when(configurationDao.lockActiveByTarget(any()))
        .thenReturn(Optional.of(configuration(4L, 12L, true)));
    for (String value : List.of("2025-11-30T23:55:00Z", "2025-12-01T00:00:00Z")) {
      assertThrows(
          BookingStartInPastException.class,
          () ->
              manager.createBooking(
                  new TimeSlotBookingManager.Create(
                      target(12L), instant(value), instant("2025-12-01T01:00:00Z"), null),
                  actor,
                  actor));
    }
    verify(bookingDao, never()).saveAndFlush(any());
    verify(events, never()).publishEvent(any(TimeSlotBookingAuditEvent.class));
  }

  @Test
  void rejectsMovingAFutureBookingIntoThePast() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    stubReadableBookingAndConfigurationLock(existing);
    assertThrows(
        BookingStartInPastException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(
                    instant("2025-11-30T23:55:00Z"),
                    instant("2025-12-01T01:00:00Z"),
                    false,
                    null,
                    null),
                actor,
                actor));
    assertEquals(start(), existing.getStartTime());
    verify(bookingDao, never()).saveAndFlush(any());
    verify(events, never()).publishEvent(any(TimeSlotBookingAuditEvent.class));
  }

  @Test
  void rejectsInvalidWindowsDisabledTargetsAndOverlapsBeforeSaving() {
    ResolvedBookableTarget target = target(12L);

    assertThrows(
        BookingWindowException.class,
        () ->
            manager.createBooking(
                new TimeSlotBookingManager.Create(target, end(), start(), null), actor, actor));

    when(configurationDao.lockActiveByTarget(target.reference()))
        .thenReturn(Optional.of(configuration(4L, 12L, false)))
        .thenReturn(Optional.of(configuration(4L, 12L, true)));
    assertThrows(
        BookingTargetUnavailableException.class,
        () ->
            manager.createBooking(
                new TimeSlotBookingManager.Create(target, start(), end(), null), actor, actor));
    TimeSlotBooking blocking = booking(59L, 12L, mock(User.class));
    when(bookingDao.findFirstOverlap(
            4L,
            start(),
            end(),
            null,
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE)))
        .thenReturn(Optional.of(blocking));
    BookingOverlapException overlap =
        assertThrows(
            BookingOverlapException.class,
            () ->
                manager.createBooking(
                    new TimeSlotBookingManager.Create(target, start(), end(), null), actor, actor));
    assertEquals(
        new ConflictingEvent(59L, BookingEventKind.BOOKING, start().toInstant(), end().toInstant()),
        overlap.conflict());

    verify(bookingDao, never()).saveAndFlush(any());
  }

  @Test
  void directSysadminCreatesMaintenanceOutsideOpeningHoursAndConfiguredMaximum() {
    when(actor.hasSysadminRole()).thenReturn(true);
    ResolvedBookableTarget target = target(12L);
    BookingConfiguration configuration = configuration(4L, 12L, true);
    configuration.setTimeZone("UTC");
    configuration.setOpeningHours(hours("08:00", "18:00"));
    configuration.setMaxBookingDurationMinutes(30);
    when(configurationDao.lockActiveByTarget(target.reference()))
        .thenReturn(Optional.of(configuration));

    TimeSlotBooking created =
        manager.createBooking(
            new TimeSlotBookingManager.Create(
                target,
                instant("2026-10-26T22:00:00Z"),
                instant("2026-10-27T02:00:00Z"),
                "Service optics",
                BookingEventKind.MAINTENANCE),
            actor,
            actor);

    assertEquals(BookingEventKind.MAINTENANCE, created.getKind());
    assertSame(actor, created.getCreatedBy());
    assertTrue(created.isCanEdit());
    verify(bookingDao)
        .findFirstOverlap(
            4L,
            instant("2026-10-26T22:00:00Z"),
            instant("2026-10-27T02:00:00Z"),
            null,
            CONFLICTING_EVENT_KINDS);
    verify(bookingNotificationService, never()).notify(any(), any(), any());
  }

  @Test
  void rejectsMaintenanceForABookerRegardlessOfTheAuditActor() {
    ResolvedBookableTarget target = target(12L);
    BookingConfiguration configuration = configuration(4L, 12L, true);
    when(configurationDao.lockActiveByTarget(target.reference()))
        .thenReturn(Optional.of(configuration));
    when(accessManager.resolveForMutation(configuration, actor)).thenReturn(bookerAccess());
    TimeSlotBookingManager.Create maintenance =
        new TimeSlotBookingManager.Create(
            target, start(), end(), null, BookingEventKind.MAINTENANCE);

    assertThrows(
        AuthorizationException.class, () -> manager.createBooking(maintenance, actor, actor));

    User sysadminAuditActor = mock(User.class);
    when(sysadminAuditActor.hasSysadminRole()).thenReturn(true);
    assertThrows(
        AuthorizationException.class,
        () -> manager.createBooking(maintenance, actor, sysadminAuditActor));

    verify(configurationDao, times(2)).lockActiveByTarget(target.reference());
  }

  @Test
  void requiresBothEndpointsToAlignToConfiguredGranularity() {
    BookingConfiguration configuration = configuration(4L, 12L, true);
    configuration.setTimeZone("UTC");
    configuration.setSlotGranularityMinutes(15);

    for (String instant : List.of("2026-08-17T10:01:00Z", "2026-08-17T10:00:01Z")) {
      BookingPolicyException failure =
          assertThrows(
              BookingPolicyException.class,
              () ->
                  schedulingPolicy.validate(
                      configuration,
                      Date.from(Instant.parse(instant)),
                      instant("2026-08-17T11:00:00Z")));
      assertEquals(BookingPolicyException.Reason.GRANULARITY, failure.reason());
    }
  }

  @Test
  void maximumDurationUsesElapsedInstantsAndAcceptsTheExactBoundary() {
    BookingConfiguration configuration = configuration(4L, 12L, true);
    configuration.setSlotGranularityMinutes(1);
    configuration.setMaxBookingDurationMinutes(60);

    assertDoesNotThrow(
        () ->
            schedulingPolicy.validate(
                configuration, instant("2026-10-25T00:30:00Z"), instant("2026-10-25T01:30:00Z")));

    BookingPolicyException failure =
        assertThrows(
            BookingPolicyException.class,
            () ->
                schedulingPolicy.validate(
                    configuration,
                    instant("2026-10-25T00:30:00Z"),
                    instant("2026-10-25T01:31:00Z")));
    assertEquals(BookingPolicyException.Reason.MAXIMUM_DURATION, failure.reason());
    assertEquals(java.util.OptionalLong.of(60), failure.maximumDurationMinutes());
  }

  @Test
  void maximumDurationAppliesToCreateAndTimeChangingUpdatesEvenWithDoubleBooking() {
    ResolvedBookableTarget target = target(12L);
    BookingConfiguration configuration = configuration(4L, 12L, true);
    configuration.setMaxBookingDurationMinutes(60);
    configuration.setAllowDoubleBooking(true);
    when(configurationDao.lockActiveByTarget(target.reference()))
        .thenReturn(Optional.of(configuration));

    assertThrows(
        BookingPolicyException.class,
        () ->
            manager.createBooking(
                new TimeSlotBookingManager.Create(target, start(), end(), null), actor, actor));
    verify(bookingDao, never()).findFirstOverlap(any(), any(), any(), any(), any());
    verify(bookingDao, never()).saveAndFlush(any());

    TimeSlotBooking existing = booking(41L, 12L, actor);
    existing.getBookingConfiguration().setMaxBookingDurationMinutes(60);
    stubReadableBookingAndConfigurationLock(existing);

    BookingPolicyException failure =
        assertThrows(
            BookingPolicyException.class,
            () ->
                manager.updateBooking(
                    41L,
                    new TimeSlotBookingManager.Patch(
                        null, instant("2026-10-25T09:05:00Z"), false, null, null),
                    actor,
                    actor));
    assertEquals(BookingPolicyException.Reason.MAXIMUM_DURATION, failure.reason());
  }

  @Test
  void checksRawOverlapThenTheSharedBufferAndLimitsDoubleBookingToMaintenance() {
    ResolvedBookableTarget target = target(12L);
    BookingConfiguration buffered = configuration(4L, 12L, true);
    buffered.setTimeZone("UTC");
    buffered.setBufferBeforeMinutes(10);
    buffered.setBufferAfterMinutes(20);
    when(configurationDao.lockActiveByTarget(target.reference())).thenReturn(Optional.of(buffered));

    manager.createBooking(
        new TimeSlotBookingManager.Create(
            target, instant("2026-08-17T10:00:00Z"), instant("2026-08-17T11:00:00Z"), null),
        actor,
        actor);

    InOrder interactions = inOrder(bookingDao);
    interactions
        .verify(bookingDao)
        .findFirstOverlap(
            4L,
            instant("2026-08-17T10:00:00Z"),
            instant("2026-08-17T11:00:00Z"),
            null,
            CONFLICTING_EVENT_KINDS);
    interactions
        .verify(bookingDao)
        .findFirstOverlap(
            4L,
            instant("2026-08-17T09:40:00Z"),
            instant("2026-08-17T11:20:00Z"),
            null,
            CONFLICTING_EVENT_KINDS);

    BookingConfiguration doubleBookable = configuration(5L, 12L, true);
    doubleBookable.setTimeZone("UTC");
    doubleBookable.setAllowDoubleBooking(true);
    when(configurationDao.lockActiveByTarget(target.reference()))
        .thenReturn(Optional.of(doubleBookable));
    manager.createBooking(
        new TimeSlotBookingManager.Create(
            target, instant("2026-08-17T12:00:00Z"), instant("2026-08-17T13:00:00Z"), null),
        actor,
        actor);
    interactions
        .verify(bookingDao)
        .findFirstOverlap(
            5L,
            instant("2026-08-17T12:00:00Z"),
            instant("2026-08-17T13:00:00Z"),
            null,
            Set.of(BookingEventKind.MAINTENANCE));
  }

  @Test
  void timeChangingEditChecksRawThenBufferedAndExcludesItself() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    BookingConfiguration configuration = existing.getBookingConfiguration();
    configuration.setTimeZone("UTC");
    configuration.setBufferBeforeMinutes(10);
    configuration.setBufferAfterMinutes(20);
    stubReadableBookingAndConfigurationLock(existing);
    Date movedStart = instant("2026-10-25T10:00:00Z");
    Date movedEnd = instant("2026-10-25T11:30:00Z");

    manager.updateBooking(
        41L,
        new TimeSlotBookingManager.Patch(movedStart, movedEnd, false, null, null),
        actor,
        actor);

    InOrder interactions = inOrder(bookingDao);
    interactions
        .verify(bookingDao)
        .findFirstOverlap(4L, movedStart, movedEnd, 41L, CONFLICTING_EVENT_KINDS);
    interactions
        .verify(bookingDao)
        .findFirstOverlap(
            4L,
            instant("2026-10-25T09:40:00Z"),
            instant("2026-10-25T11:50:00Z"),
            41L,
            CONFLICTING_EVENT_KINDS);
  }

  @Test
  void reportsARequestedIntervalOverlapWithoutCheckingTheBuffer() {
    ResolvedBookableTarget target = target(12L);
    BookingConfiguration buffered = bufferedConfiguration();
    when(configurationDao.lockActiveByTarget(target.reference())).thenReturn(Optional.of(buffered));
    TimeSlotBooking blocking =
        event(59L, BookingEventKind.BOOKING, "2026-08-17T10:30:00Z", "2026-08-17T12:00:00Z");
    when(bookingDao.findFirstOverlap(
            4L,
            instant("2026-08-17T10:00:00Z"),
            instant("2026-08-17T11:00:00Z"),
            null,
            CONFLICTING_EVENT_KINDS))
        .thenReturn(Optional.of(blocking));

    BookingOverlapException failure =
        assertThrows(
            BookingOverlapException.class,
            () ->
                manager.createBooking(
                    new TimeSlotBookingManager.Create(
                        target,
                        instant("2026-08-17T10:00:00Z"),
                        instant("2026-08-17T11:00:00Z"),
                        null),
                    actor,
                    actor));

    assertEquals(
        new ConflictingEvent(
            59L,
            BookingEventKind.BOOKING,
            Instant.parse("2026-08-17T10:30:00Z"),
            Instant.parse("2026-08-17T12:00:00Z")),
        failure.conflict());
    verify(bookingDao, times(1)).findFirstOverlap(any(), any(), any(), any(), any());
    verify(bookingDao, never()).saveAndFlush(any());
  }

  @Test
  void reportsABufferConflictWhenOnlyTheExpandedIntervalOverlaps() {
    ResolvedBookableTarget target = target(12L);
    BookingConfiguration buffered = bufferedConfiguration();
    when(configurationDao.lockActiveByTarget(target.reference())).thenReturn(Optional.of(buffered));
    TimeSlotBooking maintenance =
        event(60L, BookingEventKind.MAINTENANCE, "2026-08-17T09:00:00Z", "2026-08-17T09:45:00Z");
    when(bookingDao.findFirstOverlap(
            4L,
            instant("2026-08-17T09:40:00Z"),
            instant("2026-08-17T11:20:00Z"),
            null,
            CONFLICTING_EVENT_KINDS))
        .thenReturn(Optional.of(maintenance));

    BookingBufferConflictException failure =
        assertThrows(
            BookingBufferConflictException.class,
            () ->
                manager.createBooking(
                    new TimeSlotBookingManager.Create(
                        target,
                        instant("2026-08-17T10:00:00Z"),
                        instant("2026-08-17T11:00:00Z"),
                        null),
                    actor,
                    actor));

    assertEquals(
        new ConflictingEvent(
            60L,
            BookingEventKind.MAINTENANCE,
            Instant.parse("2026-08-17T09:00:00Z"),
            Instant.parse("2026-08-17T09:45:00Z")),
        failure.conflict());
    assertEquals(10, failure.bufferBeforeMinutes());
    assertEquals(20, failure.bufferAfterMinutes());
    verify(bookingDao, never()).saveAndFlush(any());
    verify(bookingNotificationService, never()).notify(any(), any(), any());
  }

  @Test
  void timeEditsReportBufferConflictsAndExcludeTheEditedBooking() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    BookingConfiguration buffered = existing.getBookingConfiguration();
    buffered.setTimeZone("UTC");
    buffered.setBufferBeforeMinutes(10);
    buffered.setBufferAfterMinutes(20);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L)).thenReturn(Optional.of(buffered));
    TimeSlotBooking following =
        event(61L, BookingEventKind.BOOKING, "2026-10-25T08:50:00Z", "2026-10-25T10:00:00Z");
    when(bookingDao.findFirstOverlap(
            4L,
            instant("2026-10-25T07:10:00Z"),
            instant("2026-10-25T09:05:00Z"),
            41L,
            CONFLICTING_EVENT_KINDS))
        .thenReturn(Optional.of(following));

    BookingBufferConflictException failure =
        assertThrows(
            BookingBufferConflictException.class,
            () ->
                manager.updateBooking(
                    41L,
                    new TimeSlotBookingManager.Patch(
                        null, instant("2026-10-25T08:45:00Z"), false, null, null),
                    actor,
                    actor));

    assertEquals(61L, failure.conflict().id());
    InOrder interactions = inOrder(bookingDao);
    interactions
        .verify(bookingDao)
        .findFirstOverlap(
            4L,
            instant("2026-10-25T07:30:00Z"),
            instant("2026-10-25T08:45:00Z"),
            41L,
            CONFLICTING_EVENT_KINDS);
    interactions
        .verify(bookingDao)
        .findFirstOverlap(
            4L,
            instant("2026-10-25T07:10:00Z"),
            instant("2026-10-25T09:05:00Z"),
            41L,
            CONFLICTING_EVENT_KINDS);
    assertEquals(end(), existing.getEndTime());
    verify(bookingDao, never()).saveAndFlush(any());
  }

  @Test
  void rejectsExcessiveDurationsBeforeTakingTheConfigurationLock() {
    ResolvedBookableTarget target = target(12L);

    assertThrows(
        BookingDurationException.class,
        () ->
            manager.createBooking(
                new TimeSlotBookingManager.Create(
                    target, instant("2026-01-01T00:00:00Z"), instant("2027-01-03T00:00:00Z"), null),
                actor,
                actor));

    verify(configurationDao, never()).lockActiveByTarget(any());
  }

  @Test
  void boundsOpeningCoverageByTheAbsoluteLimitEvenWithoutAnItemLimit() {
    ResolvedBookableTarget target = target(12L);
    BookingConfiguration configuration = configuration(4L, 12L, true);
    configuration.setTimeZone("UTC");
    configuration.setMaxBookingDurationMinutes(0);
    when(configurationDao.lockActiveByTarget(target.reference()))
        .thenReturn(Optional.of(configuration));
    Instant start = Instant.parse("2026-01-05T00:00:00Z");
    Instant atLimit =
        start.plus(
            java.time.Duration.ofMinutes(
                com.researchspace.model.booking.BookingSchedulingSettings
                    .MAX_BOOKING_DURATION_MINUTES));

    assertEquals(
        Date.from(atLimit),
        manager
            .createBooking(
                new TimeSlotBookingManager.Create(
                    target, Date.from(start), Date.from(atLimit), null),
                actor,
                actor)
            .getEndTime());
    assertThrows(
        BookingDurationException.class,
        () ->
            manager.createBooking(
                new TimeSlotBookingManager.Create(
                    target,
                    Date.from(start),
                    Date.from(atLimit.plus(java.time.Duration.ofMinutes(1))),
                    null),
                actor,
                actor));
    assertThrows(
        BookingDurationException.class,
        () ->
            manager.createBooking(
                new TimeSlotBookingManager.Create(
                    target, Date.from(start), instant("2626-01-05T00:00:00Z"), null),
                actor,
                actor));
    verify(configurationDao, times(1)).lockActiveByTarget(target.reference());
  }

  @Test
  void maintenanceMayUseAClosedWeekdayButARegularBookingMayNot() {
    when(actor.hasSysadminRole()).thenReturn(true);
    ResolvedBookableTarget target = target(12L);
    BookingConfiguration configuration = configuration(4L, 12L, true);
    configuration.setTimeZone("UTC");
    configuration.setOpeningHours(hours("08:00", "18:00", 3));
    when(configurationDao.lockActiveByTarget(target.reference()))
        .thenReturn(Optional.of(configuration));

    TimeSlotBooking maintenance =
        manager.createBooking(
            new TimeSlotBookingManager.Create(
                target,
                instant("2026-10-26T22:00:00Z"),
                instant("2026-10-27T02:00:00Z"),
                "Service optics",
                BookingEventKind.MAINTENANCE),
            actor,
            actor);
    BookingPolicyException failure =
        assertThrows(
            BookingPolicyException.class,
            () ->
                manager.createBooking(
                    new TimeSlotBookingManager.Create(
                        target,
                        instant("2026-10-26T10:00:00Z"),
                        instant("2026-10-26T11:00:00Z"),
                        null),
                    actor,
                    actor));

    assertEquals(BookingEventKind.MAINTENANCE, maintenance.getKind());
    assertEquals(BookingPolicyException.Reason.OPENING_HOURS, failure.reason());
  }

  @ParameterizedTest
  @MethodSource("purposeOnlyConfigurationChanges")
  void purposeOnlyEditsRemainAllowedAfterConfigurationChanges(
      Boolean enabled, Long maxBookingDurationMinutes) {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    BookingConfiguration configuration = existing.getBookingConfiguration();
    if (enabled != null) {
      configuration.setEnabled(enabled);
    }
    if (maxBookingDurationMinutes != null) {
      configuration.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
    }
    stubReadableBookingAndConfigurationLock(existing);

    TimeSlotBooking updated =
        manager
            .updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, true, "Changed", null),
                actor,
                actor)
            .orElseThrow();

    assertEquals("Changed", updated.getPurpose());
    verify(bookingDao, never()).findFirstOverlap(any(), any(), any(), any(), any());
  }

  private static Stream<Arguments> purposeOnlyConfigurationChanges() {
    return Stream.of(Arguments.of(false, null), Arguments.of(null, 60L));
  }

  @Test
  void preparesCurrentRoleRowsWithOneBatchedAccessResolution() {
    User other = mock(User.class);
    TimeSlotBooking requested = booking(1L, 12L, actor);
    TimeSlotBooking busy = booking(2L, 13L, other);
    when(bookingDao.getReadableResources(any(), any()))
        .thenReturn(new ResourcePage<>(List.of(requested, busy), 2));

    ResourcePage<TimeSlotBooking> page = manager.getBookings(ResourceRequest.unpaged(null), actor);

    assertEquals(BookingPrivacy.FULL, page.resources().get(0).getPrivacy());
    assertTrue(page.resources().get(0).isCanEdit());
    assertTrue(page.resources().get(0).isCanViewConfiguration());
    assertEquals(BookingPrivacy.FULL, page.resources().get(1).getPrivacy());
    assertTrue(page.resources().get(1).isCanEdit());
    assertTrue(page.resources().get(1).isCanViewConfiguration());
    verify(accessManager).resolveAll(any(), eq(actor));
  }

  @Test
  void preparesRolelessRequesterOwnRowAsFullButNonNavigableAndReadOnly() {
    TimeSlotBooking requested = booking(1L, 12L, actor);
    when(bookingDao.getReadableResources(any(), any()))
        .thenReturn(new ResourcePage<>(List.of(requested), 1));
    when(accessManager.resolveAll(any(), eq(actor)))
        .thenReturn(Map.of(requested.getBookingConfiguration().getId(), noAccess()));

    TimeSlotBooking prepared =
        manager.getBookings(ResourceRequest.unpaged(null), actor).resources().get(0);

    assertEquals(BookingPrivacy.FULL, prepared.getPrivacy());
    assertFalse(prepared.isCanEdit());
    assertFalse(prepared.isCanViewConfiguration());
    assertNull(prepared.getVisibleTarget());
    assertNull(prepared.getVisibleTimeZone());
    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L),
        prepared.getBookingConfiguration().getTarget());
  }

  @Test
  void maintenanceRequesterWithBookerAccessDoesNotGetOwnBookingCapabilities() {
    TimeSlotBooking maintenance = booking(1L, 12L, actor);
    maintenance.setKind(BookingEventKind.MAINTENANCE);
    maintenance.setCreatedBy(actor);
    when(bookingDao.getReadableResources(any(), any()))
        .thenReturn(new ResourcePage<>(List.of(maintenance), 1));
    when(accessManager.resolveAll(any(), eq(actor)))
        .thenReturn(Map.of(maintenance.getBookingConfiguration().getId(), bookerAccess()));

    TimeSlotBooking prepared =
        manager.getBookings(ResourceRequest.unpaged(null), actor).resources().get(0);

    assertFalse(prepared.isCanEdit());
    assertFalse(prepared.isCanCancel());
  }

  @Test
  void personalCalendarOmitsTheHiddenItemNameAndReference() throws Exception {
    TimeSlotBooking requested = booking(1L, 12L, actor);
    when(bookingDao.findUserCalendarBookings(eq(actor.getId()), any(), eq(2)))
        .thenReturn(List.of(requested));
    when(accessManager.resolveAll(any(), eq(actor)))
        .thenReturn(Map.of(requested.getBookingConfiguration().getId(), noAccess()));

    TimeSlotBookingManager.CalendarSource source =
        manager.getUserCalendarSource(actor, new Date(), 1);

    assertEquals("booking:calendar.feed.myBookings", source.itemName());
    assertTrue(source.translateName());
    assertEquals(1, source.events().size());
    assertNull(source.events().get(0).itemName());
    assertFalse(requested.isCanViewConfiguration());
    verify(instrumentDao).getNamesByIds(Set.of());
  }

  @Test
  void requesterCanCancelButCannotReinstate() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    stubReadableBookingAndConfigurationLock(existing);

    TimeSlotBooking cancelled =
        manager
            .updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CANCELLED),
                actor,
                actor)
            .orElseThrow();

    assertEquals(BookingState.CANCELLED, cancelled.getState());
    assertFalse(cancelled.isDeleted());
    assertFalse(cancelled.isCanEdit());
    assertFalse(cancelled.isCanCancel());
    verify(events)
        .publishEvent(
            (Object)
                argThat(
                    event ->
                        event instanceof TimeSlotBookingAuditEvent bookingEvent
                            && bookingEvent.action() == AuditAction.WRITE));
    TimeSlotBooking retried =
        manager
            .updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CANCELLED),
                cancelled.getVersion(),
                actor,
                actor)
            .orElseThrow();
    assertSame(cancelled, retried);
    verify(bookingDao, times(1)).saveAndFlush(any(TimeSlotBooking.class));
    verify(events, times(1)).publishEvent(any(TimeSlotBookingAuditEvent.class));
    verify(bookingNotificationService, times(1))
        .notify(cancelled, actor, NotificationType.NOTIFICATION_BOOKING_CANCELLED);
    assertThrows(
        BookingStateTransitionException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CONFIRMED),
                actor,
                actor));
    assertThrows(
        BookingStateTransitionException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, true, "Changed", null),
                actor,
                actor));
  }

  @Test
  void anotherTargetReaderCannotEdit() {
    User requester = mock(User.class);
    TimeSlotBooking existing = booking(41L, 12L, requester);
    stubReadableBookingAndConfigurationLock(existing);
    when(accessManager.resolveForMutation(existing.getBookingConfiguration(), actor))
        .thenReturn(viewerAccess());

    assertThrows(
        AuthorizationException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, true, "Changed", null),
                actor,
                actor));
    verify(configurationDao).lockActiveById(4L);
  }

  @Test
  void directSysadminCanEditAndCancelMaintenance() {
    when(actor.hasSysadminRole()).thenReturn(true);
    TimeSlotBooking maintenance = maintenanceBooking(41L, 12L, actor);
    maintenance.setCreatedBy(actor);
    stubReadableBookingAndConfigurationLock(maintenance);

    TimeSlotBooking updated =
        manager
            .updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, true, "Service complete", null),
                actor,
                actor)
            .orElseThrow();
    assertEquals("Service complete", updated.getPurpose());

    TimeSlotBooking cancelled =
        manager
            .updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CANCELLED),
                actor,
                actor)
            .orElseThrow();
    assertEquals(BookingState.CANCELLED, cancelled.getState());
    verify(bookingNotificationService, never()).notify(any(), any(), any());
  }

  @Test
  void bookerCannotMutateMaintenanceRegardlessOfTheAuditActor() {
    TimeSlotBooking maintenance = maintenanceBooking(41L, 12L, actor);
    stubReadableBookingAndConfigurationLock(maintenance);
    when(accessManager.resolveForMutation(maintenance.getBookingConfiguration(), actor))
        .thenReturn(bookerAccess());

    assertThrows(
        AuthorizationException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, true, "Changed", null),
                actor,
                actor));

    User sysadminAuditActor = mock(User.class);
    when(sysadminAuditActor.hasSysadminRole()).thenReturn(true);
    assertThrows(
        AuthorizationException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, true, "Changed", null),
                actor,
                sysadminAuditActor));
    verify(configurationDao, times(2)).lockActiveById(4L);
  }

  @Test
  void mapsAStaleWriteToTheStableConcurrencyException() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    stubReadableBookingAndConfigurationLock(existing);
    when(bookingDao.saveAndFlush(existing))
        .thenThrow(new ObjectOptimisticLockingFailureException(TimeSlotBooking.class, 41L));

    assertThrows(
        BookingConcurrentModificationException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, true, "Changed", null),
                actor,
                actor));
  }

  @Test
  void unreadableBookingIsConcealedRegardlessOfExpectedVersion() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    stubReadableBookingAndConfigurationLock(existing);
    when(accessManager.resolveForMutation(existing.getBookingConfiguration(), actor))
        .thenReturn(noAccess());

    for (long version : new long[] {existing.getVersion(), existing.getVersion() + 1}) {
      assertTrue(
          manager
              .updateBooking(
                  41L,
                  new TimeSlotBookingManager.Patch(null, null, true, "Changed", null),
                  version,
                  actor,
                  actor)
              .isEmpty());
    }
    assertEquals("Private purpose", existing.getPurpose());
  }

  private void stubReadableBookingAndConfigurationLock(TimeSlotBooking booking) {
    when(bookingDao.findReadableById(eq(booking.getId()), any())).thenReturn(Optional.of(booking));
    when(configurationDao.lockActiveById(booking.getBookingConfiguration().getId()))
        .thenReturn(Optional.of(booking.getBookingConfiguration()));
  }

  private static TimeSlotBooking maintenanceBooking(long id, long targetId, User requester) {
    TimeSlotBooking booking = booking(id, targetId, requester);
    booking.setKind(BookingEventKind.MAINTENANCE);
    return booking;
  }

  private static TimeSlotBooking booking(long id, long targetId, User requester) {
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(id);
    booking.setBookingConfiguration(configuration(4L, targetId, true));
    booking.setRequester(requester);
    booking.setStartTime(start());
    booking.setEndTime(end());
    booking.setState(BookingState.CONFIRMED);
    booking.setPurpose("Private purpose");
    return booking;
  }

  private static BookingConfiguration configuration(long id, long targetId, boolean enabled) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(id);
    configuration.setEnabled(enabled);
    configuration.setTimeZone("Europe/Berlin");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, targetId));
    ResourceAccess access =
        new ResourceAccess(BookingResourceRoleScheme.SCHEME_KEY, null, new Date());
    access.setId(targetId);
    configuration.setResourceAccess(access);
    return configuration;
  }

  private static BookingConfiguration bufferedConfiguration() {
    BookingConfiguration buffered = configuration(4L, 12L, true);
    buffered.setTimeZone("UTC");
    buffered.setBufferBeforeMinutes(10);
    buffered.setBufferAfterMinutes(20);
    return buffered;
  }

  private static TimeSlotBooking event(
      long id, BookingEventKind kind, String startValue, String endValue) {
    TimeSlotBooking event = new TimeSlotBooking();
    event.setId(id);
    event.setKind(kind);
    event.setStartTime(instant(startValue));
    event.setEndTime(instant(endValue));
    event.setPurpose("Private purpose");
    return event;
  }

  private static ResolvedResourceAccess ownerAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.OWNER),
        Set.of(
            BookingResourceRoleScheme.READ_RESOURCE,
            BookingResourceRoleScheme.CREATE_BOOKING,
            BookingResourceRoleScheme.CREATE_BLOCKOUT,
            BookingResourceRoleScheme.MANAGE_OWN_BOOKINGS,
            BookingResourceRoleScheme.MANAGE_ALL_EVENTS),
        List.of());
  }

  private static ResolvedResourceAccess noAccess() {
    return new ResolvedResourceAccess(Optional.empty(), Set.of(), List.of());
  }

  private static ResolvedResourceAccess bookerAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.BOOKER),
        Set.of(
            BookingResourceRoleScheme.READ_RESOURCE,
            BookingResourceRoleScheme.CREATE_BOOKING,
            BookingResourceRoleScheme.MANAGE_OWN_BOOKINGS),
        List.of());
  }

  private static ResolvedResourceAccess viewerAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.VIEWER),
        Set.of(BookingResourceRoleScheme.READ_RESOURCE),
        List.of());
  }

  private static ResolvedBookableTarget target(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return new ResolvedBookableTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, id), instrument);
  }

  private static Date start() {
    return Date.from(Instant.parse("2026-10-25T07:30:00Z"));
  }

  private static Date end() {
    return Date.from(Instant.parse("2026-10-25T09:00:00Z"));
  }

  private static Date instant(String value) {
    return Date.from(Instant.parse(value));
  }
}
