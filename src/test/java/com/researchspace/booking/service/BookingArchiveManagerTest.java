package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import jakarta.ws.rs.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class BookingArchiveManagerTest {

  private final BookingConfigurationDao configurations = mock(BookingConfigurationDao.class);
  private final TimeSlotBookingDao bookings = mock(TimeSlotBookingDao.class);
  private final InstrumentDao instruments = mock(InstrumentDao.class);
  private final com.researchspace.service.resourceaccess.ResourceAccessManager access =
      mock(com.researchspace.service.resourceaccess.ResourceAccessManager.class);
  private final FeatureFlagManager featureFlags = mock(FeatureFlagManager.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final User subject = mock(User.class);
  private final User actor = mock(User.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);
  private final BookingArchiveManager manager =
      new BookingArchiveManager(
          configurations, bookings, instruments, access, featureFlags, events, clock);

  @BeforeEach
  void setUp() {
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)).thenReturn(true);
  }

  @Test
  void summaryIsNarrowAndLetsManagersCancelButNotUnarchive() {
    BookingConfiguration configuration = archivedConfiguration();
    when(configurations.findArchivedById(7L)).thenReturn(Optional.of(configuration));
    when(access.resolve(configuration.getResourceAccess(), subject))
        .thenReturn(
            new ResolvedResourceAccess(
                Optional.of(BookingResourceRoleScheme.MANAGER),
                Set.of(
                    BookingResourceRoleScheme.READ_RESOURCE,
                    BookingResourceRoleScheme.MANAGE_ALL_EVENTS),
                List.of()));
    Instrument instrument = mock(Instrument.class);
    when(instrument.isDeleted()).thenReturn(false);
    when(instrument.getGlobalIdentifier()).thenReturn("IN12");
    when(instrument.getName()).thenReturn("Confocal microscope");
    when(instruments.getSafeNull(12L)).thenReturn(Optional.of(instrument));
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setId(19L);
    booking.setStartTime(Date.from(Instant.parse("2026-10-01T10:00:00Z")));
    booking.setEndTime(Date.from(Instant.parse("2026-10-01T11:00:00Z")));
    booking.setVersion(2L);
    when(bookings.findFutureConfirmedByConfiguration(7L, Date.from(clock.instant())))
        .thenReturn(List.of(booking));

    BookingArchiveManager.Summary summary = manager.summary(7L, subject);

    assertEquals("IN12", summary.target().globalId());
    assertFalse(summary.canUnarchive());
    assertTrue(summary.canCancelBookings());
    assertFalse(summary.calendarSubscriptionActive());
    assertEquals(19L, summary.futureBookings().get(0).id());
    assertFalse(summary.futureBookings().get(0).canEdit());
    assertTrue(summary.futureBookings().get(0).canCancel());
  }

  @Test
  void ownerCanUnarchiveOnlyAtTheExpectedVersion() {
    BookingConfiguration configuration = archivedConfiguration();
    when(configurations.lockArchivedById(7L)).thenReturn(Optional.of(configuration));
    when(configurations.saveAndFlush(configuration)).thenReturn(configuration);
    when(access.resolve(configuration.getResourceAccess(), subject))
        .thenReturn(
            new ResolvedResourceAccess(
                Optional.of(BookingResourceRoleScheme.OWNER),
                Set.of(
                    BookingResourceRoleScheme.READ_RESOURCE,
                    BookingResourceRoleScheme.ARCHIVE_CONFIGURATION),
                List.of()));

    BookingConfiguration restored = manager.unarchive(7L, 3L, subject, actor);

    assertFalse(restored.isDeleted());
    assertEquals(actor, restored.getUpdatedBy());
    verify(configurations).saveAndFlush(configuration);
    verify(events)
        .publishEvent(org.mockito.ArgumentMatchers.any(BookingConfigurationAuditEvent.class));
  }

  @Test
  void staleUnarchiveDoesNotWrite() {
    BookingConfiguration configuration = archivedConfiguration();
    when(configurations.lockArchivedById(7L)).thenReturn(Optional.of(configuration));
    when(access.resolve(configuration.getResourceAccess(), subject))
        .thenReturn(
            new ResolvedResourceAccess(
                Optional.of(BookingResourceRoleScheme.OWNER),
                Set.of(
                    BookingResourceRoleScheme.READ_RESOURCE,
                    BookingResourceRoleScheme.ARCHIVE_CONFIGURATION),
                List.of()));

    assertThrows(
        BookingConcurrentModificationException.class,
        () -> manager.unarchive(7L, 2L, subject, actor));
    verify(configurations, never()).saveAndFlush(configuration);
  }

  @Test
  void featureOffConcealsReadsAndForbidsMutations() {
    when(featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> manager.summary(7L, subject));
    assertThrows(AuthorizationException.class, () -> manager.unarchive(7L, 3L, subject, actor));
  }

  private static BookingConfiguration archivedConfiguration() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(7L);
    configuration.setDeleted(true);
    configuration.setConfigurationVersion(3L);
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L));
    configuration.setResourceAccess(
        new ResourceAccess(
            BookingResourceRoleScheme.SCHEME_KEY,
            null,
            Date.from(Instant.parse("2026-08-01T10:00:00Z"))));
    return configuration;
  }
}
