package com.researchspace.booking.service;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class TimeSlotBookingManagerTest {

  private final TimeSlotBookingDao bookingDao = mock(TimeSlotBookingDao.class);
  private final BookingConfigurationDao configurationDao = mock(BookingConfigurationDao.class);
  private final InstrumentDao instrumentDao = mock(InstrumentDao.class);
  private final ObjectProvider<ResourceRegistry> registry = mock(ObjectProvider.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final BookingSchedulingPolicy schedulingPolicy = new BookingSchedulingPolicyImpl();
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
                    ApiV2UserResource.DESCRIPTION,
                    com.researchspace.model.booking.ApiV2BookingLocationResource.DESCRIPTION)));
    when(bookingDao.saveAndFlush(any(TimeSlotBooking.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(accessManager.resolveForMutation(any(BookingConfiguration.class), eq(actor)))
        .thenReturn(ownerAccess());
    when(accessManager.resolve(any(BookingConfiguration.class), eq(actor)))
        .thenReturn(ownerAccess());
    when(accessManager.resolveAll(any(), eq(actor)))
        .thenAnswer(
            invocation -> {
              java.util.Collection<BookingConfiguration> accesses = invocation.getArgument(0);
              return accesses.stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          BookingConfiguration::getId, ignored -> ownerAccess()));
            });
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
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(existing.getBookingConfiguration()));
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
    configuration.setOpeningStart("08:00");
    configuration.setOpeningEnd("18:00");
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
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE));
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

    verify(configurationDao, org.mockito.Mockito.times(2)).lockActiveByTarget(target.reference());
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
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(existing.getBookingConfiguration()));

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
  void expandsConflictQueriesAsymmetricallyAndSkipsThemForDoubleBooking() {
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

    org.mockito.InOrder queries = org.mockito.Mockito.inOrder(bookingDao);
    queries
        .verify(bookingDao)
        .findFirstOverlap(
            4L,
            instant("2026-08-17T10:00:00Z"),
            instant("2026-08-17T11:00:00Z"),
            null,
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE));
    queries
        .verify(bookingDao)
        .findFirstOverlap(
            4L,
            instant("2026-08-17T09:40:00Z"),
            instant("2026-08-17T11:10:00Z"),
            null,
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE));

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
    verify(bookingDao)
        .findFirstOverlap(eq(5L), any(), any(), any(), eq(Set.of(BookingEventKind.MAINTENANCE)));
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
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE)))
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
            instant("2026-08-17T11:10:00Z"),
            null,
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE)))
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
            instant("2026-10-25T08:55:00Z"),
            41L,
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE)))
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
    verify(bookingDao)
        .findFirstOverlap(
            4L,
            instant("2026-10-25T07:30:00Z"),
            instant("2026-10-25T08:45:00Z"),
            41L,
            Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE));
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
    configuration.setOpenDays(List.of(3));
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

  @Test
  void purposeOnlyEditsRemainAllowedWhenTheConfigurationIsDisabled() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    BookingConfiguration disabled = existing.getBookingConfiguration();
    disabled.setEnabled(false);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L)).thenReturn(Optional.of(disabled));

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

  @Test
  void purposeOnlyEditsRemainAllowedAfterTheMaximumIsLowered() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    BookingConfiguration configuration = existing.getBookingConfiguration();
    configuration.setMaxBookingDurationMinutes(60);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L)).thenReturn(Optional.of(configuration));

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
  void requesterCanCancelAndThenRestoreTheBooking() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(existing.getBookingConfiguration()));

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
    // A cancelled booking cannot be edited, only restored.
    assertThrows(
        BookingStateTransitionException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, true, "Changed", null),
                actor,
                actor));
    assertThrows(
        BookingStateTransitionException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(
                    null, null, true, "Changed", BookingState.CONFIRMED),
                actor,
                actor));

    TimeSlotBooking restored =
        manager
            .updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CONFIRMED),
                actor,
                actor)
            .orElseThrow();

    assertEquals(BookingState.CONFIRMED, restored.getState());
    assertNull(restored.getCancellationReason());
    verify(bookingDao, times(2)).saveAndFlush(any(TimeSlotBooking.class));
    verify(bookingNotificationService).notifyRestored(restored, actor);
    verify(events, times(2)).publishEvent(any(TimeSlotBookingAuditEvent.class));
  }

  @Test
  void restoreRequiresAFutureStartAnEnabledItemAndAFreeSlot() {
    TimeSlotBooking cancelled = booking(41L, 12L, actor);
    cancelled.setState(BookingState.CANCELLED);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(cancelled));
    TimeSlotBookingManager.Patch restore =
        new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CONFIRMED);

    cancelled.getBookingConfiguration().setEnabled(false);
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(cancelled.getBookingConfiguration()));
    assertThrows(
        BookingTargetUnavailableException.class,
        () -> manager.updateBooking(41L, restore, actor, actor));

    cancelled.getBookingConfiguration().setEnabled(true);
    TimeSlotBooking taken =
        event(
            59L,
            BookingEventKind.BOOKING,
            start().toInstant().toString(),
            end().toInstant().toString());
    when(bookingDao.findFirstOverlap(eq(4L), any(), any(), eq(41L), any()))
        .thenReturn(Optional.of(taken));
    BookingOverlapException overlap =
        assertThrows(
            BookingOverlapException.class, () -> manager.updateBooking(41L, restore, actor, actor));
    assertEquals(59L, overlap.conflict().id());

    when(bookingDao.findFirstOverlap(eq(4L), any(), any(), eq(41L), any()))
        .thenReturn(Optional.empty());
    cancelled.setStartTime(Date.from(Instant.parse("2025-11-30T10:00:00Z")));
    assertThrows(
        BookingStateTransitionException.class,
        () -> manager.updateBooking(41L, restore, actor, actor));

    verify(bookingDao, never()).saveAndFlush(any());
    verify(bookingNotificationService, never()).notifyRestored(any(), any());
  }

  @Test
  void cancellationReasonIsTrimmedAndStoredOnTheCancellation() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(existing.getBookingConfiguration()));

    TimeSlotBooking cancelled =
        manager
            .updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(
                    null,
                    null,
                    false,
                    null,
                    BookingState.CANCELLED,
                    "  Instrument needs recalibration  "),
                actor,
                actor)
            .orElseThrow();

    assertEquals("Instrument needs recalibration", cancelled.getCancellationReason());
    assertEquals(BookingState.CANCELLED, cancelled.getState());
    assertEquals("Instrument needs recalibration", cancelled.getVisibleCancellationReason());
  }

  @Test
  void blankCancellationReasonIsStoredAsNull() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(existing.getBookingConfiguration()));

    manager
        .updateBooking(
            41L,
            new TimeSlotBookingManager.Patch(
                null, null, false, null, BookingState.CANCELLED, " \t\n "),
            actor,
            actor)
        .orElseThrow();

    assertNull(existing.getCancellationReason());
  }

  @Test
  void rejectsAnOverlongReasonAndAReasonWithoutCancellation() {
    assertEquals(
        "errors.api.v2.booking.cancellationReason.length",
        assertThrows(
                BookingCancellationReasonLengthException.class,
                () ->
                    manager.updateBooking(
                        41L,
                        new TimeSlotBookingManager.Patch(
                            null, null, false, null, BookingState.CANCELLED, "x".repeat(501)),
                        actor,
                        actor))
            .getMessage());
    assertEquals(
        "errors.api.v2.booking.cancellationReason.requiresCancel",
        assertThrows(
                BookingCancellationReasonRequiresCancelException.class,
                () ->
                    manager.updateBooking(
                        41L,
                        new TimeSlotBookingManager.Patch(
                            null, null, false, null, null, "Needs recalibration"),
                        actor,
                        actor))
            .getMessage());
    verify(bookingDao, never()).findReadableById(any(), any());
    verify(configurationDao, never()).lockActiveById(any());
    verify(events, never()).publishEvent(any(TimeSlotBookingAuditEvent.class));
    verify(bookingNotificationService, never()).notify(any(), any(), any());
  }

  @Test
  void repeatedCancellationRequiresTheSameReasonAndStillHonoursVersion() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    existing.setState(BookingState.CANCELLED);
    existing.setCancellationReason("Already recalibrated");
    existing.setVersion(4L);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(existing.getBookingConfiguration()));

    TimeSlotBooking replayed =
        manager
            .updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(
                    null, null, false, null, BookingState.CANCELLED, " Already recalibrated "),
                existing.getVersion(),
                actor,
                actor)
            .orElseThrow();
    assertSame(existing, replayed);
    verify(bookingDao, never()).saveAndFlush(any(TimeSlotBooking.class));
    verify(events, never()).publishEvent(any(TimeSlotBookingAuditEvent.class));
    verify(bookingNotificationService, never()).notify(any(), any(), any());

    assertThrows(
        BookingStateTransitionException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(
                    null, null, false, null, BookingState.CANCELLED, "Different reason"),
                existing.getVersion(),
                actor,
                actor));
    assertThrows(
        BookingConcurrentModificationException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(
                    null, null, false, null, BookingState.CANCELLED, "Already recalibrated"),
                existing.getVersion() - 1,
                actor,
                actor));
  }

  @Test
  void anotherTargetReaderCannotEdit() {
    User requester = mock(User.class);
    TimeSlotBooking existing = booking(41L, 12L, requester);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(existing.getBookingConfiguration()));
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
    TimeSlotBooking maintenance = booking(41L, 12L, actor);
    maintenance.setKind(BookingEventKind.MAINTENANCE);
    maintenance.setCreatedBy(actor);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(maintenance));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(maintenance.getBookingConfiguration()));

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
    TimeSlotBooking maintenance = booking(41L, 12L, actor);
    maintenance.setKind(BookingEventKind.MAINTENANCE);
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(maintenance));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(maintenance.getBookingConfiguration()));
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
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(existing.getBookingConfiguration()));
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
    when(bookingDao.findReadableById(eq(41L), any())).thenReturn(Optional.of(existing));
    when(configurationDao.lockActiveById(4L))
        .thenReturn(Optional.of(existing.getBookingConfiguration()));
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
