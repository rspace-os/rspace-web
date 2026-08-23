package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

class TimeSlotBookingManagerTest {

  private final TimeSlotBookingDao bookingDao = mock(TimeSlotBookingDao.class);
  private final BookingConfigurationDao configurationDao = mock(BookingConfigurationDao.class);
  private final InventoryPermissionUtils permissions = mock(InventoryPermissionUtils.class);
  private final ObjectProvider<ResourceRegistry> registry = mock(ObjectProvider.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final User actor = mock(User.class);
  private final TimeSlotBookingManager manager =
      new TimeSlotBookingManagerImpl(bookingDao, configurationDao, permissions, registry, events);

  @BeforeEach
  void setUp() {
    when(actor.getId()).thenReturn(1L);
    when(actor.getUsername()).thenReturn("ada");
    when(actor.getFullName()).thenReturn("Ada Lovelace");
    when(registry.getObject())
        .thenReturn(
            new ResourceRegistry(
                List.of(
                    ApiV2TimeSlotBookingResource.DESCRIPTION,
                    ApiV2InstrumentResource.DESCRIPTION)));
    when(bookingDao.saveAndFlush(any(TimeSlotBooking.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(bookingDao.findOwnedInstrumentIds(anyCollection(), eq(1L))).thenReturn(Set.of());
  }

  @Test
  void createsInsideTheConfigurationLockAndPreparesAFullResponse() {
    BookingConfiguration configuration = configuration(4L, 12L, true);
    ResolvedBookableTarget target = target(12L);
    when(permissions.canUserReadInventoryRecord((Instrument) target.entity(), actor))
        .thenReturn(true);
    when(configurationDao.lockByTarget(target.reference())).thenReturn(Optional.of(configuration));
    when(bookingDao.overlaps(4L, start(), end(), null)).thenReturn(false);

    TimeSlotBooking created =
        manager.createBooking(
            new TimeSlotBookingManager.Create(target, start(), end(), "Image plate 4"),
            actor,
            actor);

    assertSame(configuration, created.getBookingConfiguration());
    assertSame(actor, created.getRequester());
    assertEquals(BookingState.CONFIRMED, created.getState());
    assertEquals(BookingPrivacy.FULL, created.getPrivacy());
    assertTrue(created.isCanEdit());
    assertFalse(created.isDeleted());
    verify(configurationDao).lockByTarget(target.reference());
    verify(bookingDao).overlaps(4L, start(), end(), null);
    verify(events).publishEvent(any(TimeSlotBookingAuditEvent.class));
  }

  @Test
  void rejectsInvalidWindowsDisabledTargetsAndOverlapsBeforeSaving() {
    ResolvedBookableTarget target = target(12L);
    when(permissions.canUserReadInventoryRecord((Instrument) target.entity(), actor))
        .thenReturn(true);

    assertThrows(
        BookingWindowException.class,
        () ->
            manager.createBooking(
                new TimeSlotBookingManager.Create(target, end(), start(), null), actor, actor));

    when(configurationDao.lockByTarget(target.reference()))
        .thenReturn(Optional.of(configuration(4L, 12L, false)))
        .thenReturn(Optional.of(configuration(4L, 12L, true)));
    assertThrows(
        BookingTargetUnavailableException.class,
        () ->
            manager.createBooking(
                new TimeSlotBookingManager.Create(target, start(), end(), null), actor, actor));
    when(bookingDao.overlaps(4L, start(), end(), null)).thenReturn(true);
    assertThrows(
        BookingOverlapException.class,
        () ->
            manager.createBooking(
                new TimeSlotBookingManager.Create(target, start(), end(), null), actor, actor));

    verify(bookingDao, never()).saveAndFlush(any());
  }

  @Test
  void preparesMixedFullAndBusyRowsWithOneOwnershipQuery() {
    User other = mock(User.class);
    TimeSlotBooking requested = booking(1L, 12L, actor);
    TimeSlotBooking busy = booking(2L, 13L, other);
    when(bookingDao.getReadableResources(any(), any()))
        .thenReturn(new ResourcePage<>(List.of(requested, busy), 2));
    when(bookingDao.findOwnedInstrumentIds(Set.of(12L, 13L), 1L)).thenReturn(Set.of());

    ResourcePage<TimeSlotBooking> page = manager.getBookings(ResourceRequest.unpaged(null), actor);

    assertEquals(BookingPrivacy.FULL, page.resources().get(0).getPrivacy());
    assertTrue(page.resources().get(0).isCanEdit());
    assertEquals(BookingPrivacy.BUSY, page.resources().get(1).getPrivacy());
    assertFalse(page.resources().get(1).isCanEdit());
    assertEquals(null, page.resources().get(1).getVisiblePurpose());
    assertEquals(null, page.resources().get(1).getVisibleBookedBy());
    verify(bookingDao).findOwnedInstrumentIds(Set.of(12L, 13L), 1L);
  }

  @Test
  void requesterCanCancelButCannotReinstate() {
    TimeSlotBooking existing = booking(41L, 12L, actor);
    when(bookingDao.getSafeNull(41L)).thenReturn(Optional.of(existing));
    when(configurationDao.lockById(4L)).thenReturn(Optional.of(existing.getBookingConfiguration()));

    TimeSlotBooking cancelled =
        manager
            .updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, false, null, BookingState.CANCELLED),
                actor,
                actor)
            .orElseThrow();

    assertEquals(BookingState.CANCELLED, cancelled.getState());
    assertTrue(cancelled.isDeleted());
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
    when(bookingDao.getSafeNull(41L)).thenReturn(Optional.of(existing));

    assertThrows(
        AuthorizationException.class,
        () ->
            manager.updateBooking(
                41L,
                new TimeSlotBookingManager.Patch(null, null, true, "Changed", null),
                actor,
                actor));
    verify(configurationDao, never()).lockById(any());
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
    return configuration;
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
}
