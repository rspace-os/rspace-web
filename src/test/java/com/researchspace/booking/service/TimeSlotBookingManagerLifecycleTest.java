package com.researchspace.booking.service;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.ApiV2BookingLocationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class TimeSlotBookingManagerLifecycleTest {

  private final TimeSlotBookingManagerTest fixture = new TimeSlotBookingManagerTest();

  private TimeSlotBookingDao bookingDao;
  private BookingConfigurationDao configurationDao;
  private InstrumentDao instrumentDao;
  private ApplicationEventPublisher events;
  private User actor;
  private BookingItemPermissions accessManager;
  private TimeSlotBookingManager manager;
  private BookingNotificationService bookingNotificationService;

  @BeforeEach
  void setUp() {
    fixture.setUp();
    bookingDao = fixture.bookingDao;
    configurationDao = fixture.configurationDao;
    instrumentDao = fixture.instrumentDao;
    events = fixture.events;
    actor = fixture.actor;
    accessManager = fixture.accessManager;
    manager = fixture.manager;
    bookingNotificationService = fixture.bookingNotificationService;
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
  void preparesReadableParentLocationsWithOneBatchedLookup() {
    TimeSlotBooking stored = booking(1L, 12L, actor);
    TimeSlotBooking unreadable = booking(2L, 13L, actor);
    when(bookingDao.getReadableResources(any(), any()))
        .thenReturn(new ResourcePage<>(List.of(stored, unreadable), 2));
    when(instrumentDao.getReadableParentLocationSummaries(Set.of(12L, 13L), actor))
        .thenReturn(
            Map.of(
                12L,
                new InstrumentParentLocationSummary(
                    7L, "Cold room", Container.ContainerType.LIST)));

    List<TimeSlotBooking> prepared =
        manager.getBookings(ResourceRequest.unpaged(null), actor).resources();

    assertEquals(
        new ApiV2BookingLocationResource.Location(7L, "IC7", "Cold room"),
        prepared.get(0).getVisibleLocation());
    assertNull(prepared.get(1).getVisibleLocation());
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
    fixture.stubReadableBookingAndConfigurationLock(booking);
  }

  private static TimeSlotBooking booking(long id, long targetId, User requester) {
    return TimeSlotBookingManagerTest.booking(id, targetId, requester);
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

  private static TimeSlotBooking maintenanceBooking(long id, long targetId, User requester) {
    TimeSlotBooking booking = booking(id, targetId, requester);
    booking.setKind(BookingEventKind.MAINTENANCE);
    return booking;
  }

  private static ResolvedResourceAccess noAccess() {
    return new ResolvedResourceAccess(Optional.empty(), Set.of(), List.of());
  }

  private static ResolvedResourceAccess viewerAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.VIEWER),
        Set.of(BookingResourceRoleScheme.READ_RESOURCE),
        List.of());
  }
}
