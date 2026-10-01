package com.researchspace.booking.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

class TimeSlotBookingManagerTest {

  final TimeSlotBookingDao bookingDao = mock(TimeSlotBookingDao.class);
  final BookingConfigurationDao configurationDao = mock(BookingConfigurationDao.class);
  final InstrumentDao instrumentDao = mock(InstrumentDao.class);
  final BookingInstrumentQueryDao bookingInstruments = mock(BookingInstrumentQueryDao.class);
  private final ObjectProvider<ResourceRegistry> registry = mock(ObjectProvider.class);
  final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  final BookingSchedulingPolicy schedulingPolicy = new BookingSchedulingPolicy();
  final User actor = mock(User.class);
  final BookingItemPermissions accessManager = mock(BookingItemPermissions.class);
  final TimeSlotBookingManager manager =
      new TimeSlotBookingManagerImpl(
          bookingDao,
          configurationDao,
          schedulingPolicy,
          instrumentDao,
          bookingInstruments,
          registry,
          events,
          accessManager,
          ApiV2TimeSlotBookingResource.DESCRIPTION,
          ApiV2BookingConfigurationResource.DESCRIPTION,
          Clock.fixed(Instant.parse("2025-12-01T00:00:00Z"), ZoneOffset.UTC));

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

  void stubReadableBookingAndConfigurationLock(TimeSlotBooking booking) {
    when(bookingDao.findReadableById(eq(booking.getId()), any())).thenReturn(Optional.of(booking));
    when(configurationDao.lockActiveById(booking.getBookingConfiguration().getId()))
        .thenReturn(Optional.of(booking.getBookingConfiguration()));
  }

  static TimeSlotBooking booking(long id, long targetId, User requester) {
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

  static BookingConfiguration configuration(long id, long targetId, boolean enabled) {
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

  static ResolvedResourceAccess bookerAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.BOOKER),
        Set.of(
            BookingResourceRoleScheme.READ_RESOURCE,
            BookingResourceRoleScheme.CREATE_BOOKING,
            BookingResourceRoleScheme.MANAGE_OWN_BOOKINGS),
        List.of());
  }

  static Date start() {
    return Date.from(Instant.parse("2026-10-25T07:30:00Z"));
  }

  static Date end() {
    return Date.from(Instant.parse("2026-10-25T09:00:00Z"));
  }

  static Date instant(String value) {
    return Date.from(Instant.parse(value));
  }
}
