package com.researchspace.booking.service;

import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class TimeSlotBookingSchedulingTest {

  private static final Set<BookingEventKind> CONFLICTING_EVENT_KINDS =
      Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE);

  private final TimeSlotBookingManagerTest fixture = new TimeSlotBookingManagerTest();

  private TimeSlotBookingDao bookingDao;
  private BookingConfigurationDao configurationDao;
  private ApplicationEventPublisher events;
  private BookingSchedulingPolicy schedulingPolicy;
  private User actor;
  private BookingItemPermissions accessManager;
  private TimeSlotBookingManager manager;
  private BookingNotificationService bookingNotificationService;

  @BeforeEach
  void setUp() {
    fixture.setUp();
    bookingDao = fixture.bookingDao;
    configurationDao = fixture.configurationDao;
    events = fixture.events;
    schedulingPolicy = fixture.schedulingPolicy;
    actor = fixture.actor;
    accessManager = fixture.accessManager;
    manager = fixture.manager;
    bookingNotificationService = fixture.bookingNotificationService;
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
    BookingConfiguration buffered = bufferedConfiguration();
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

  private void stubReadableBookingAndConfigurationLock(TimeSlotBooking booking) {
    fixture.stubReadableBookingAndConfigurationLock(booking);
  }

  private static TimeSlotBooking booking(long id, long targetId, User requester) {
    return TimeSlotBookingManagerTest.booking(id, targetId, requester);
  }

  private static BookingConfiguration configuration(long id, long targetId, boolean enabled) {
    return TimeSlotBookingManagerTest.configuration(id, targetId, enabled);
  }

  private static ResolvedResourceAccess bookerAccess() {
    return TimeSlotBookingManagerTest.bookerAccess();
  }

  private static Date start() {
    return TimeSlotBookingManagerTest.start();
  }

  private static Date end() {
    return TimeSlotBookingManagerTest.end();
  }

  private static Date instant(String value) {
    return TimeSlotBookingManagerTest.instant(value);
  }

  private static ResolvedBookableTarget target(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return new ResolvedBookableTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, id), instrument);
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
    return TimeSlotBookingManagerTest.event(id, kind, startValue, endValue);
  }
}
