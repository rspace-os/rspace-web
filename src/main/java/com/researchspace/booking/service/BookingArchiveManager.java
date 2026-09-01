package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import jakarta.ws.rs.NotFoundException;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Narrow lifecycle service for archived Booking configurations. */
@Service
@Transactional
public class BookingArchiveManager {

  public record Target(String globalId, String name) {}

  public record FutureBooking(
      long id, Date start, Date end, long version, boolean canEdit, boolean canCancel) {
    public FutureBooking {
      start = new Date(start.getTime());
      end = new Date(end.getTime());
    }

    @Override
    public Date start() {
      return new Date(start.getTime());
    }

    @Override
    public Date end() {
      return new Date(end.getTime());
    }
  }

  public record Summary(
      long id,
      long version,
      Target target,
      boolean canUnarchive,
      boolean canCancelBookings,
      boolean calendarSubscriptionActive,
      List<FutureBooking> futureBookings) {
    public Summary {
      futureBookings = List.copyOf(futureBookings);
    }
  }

  private final BookingConfigurationDao configurations;
  private final TimeSlotBookingDao bookings;
  private final InstrumentDao instruments;
  private final ResourceAccessManager accessManager;
  private final FeatureFlagManager featureFlags;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public BookingArchiveManager(
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurations,
      @Qualifier("timeSlotBookingDao") TimeSlotBookingDao bookings,
      InstrumentDao instruments,
      ResourceAccessManager accessManager,
      FeatureFlagManager featureFlags,
      ApplicationEventPublisher events,
      @Qualifier(BookingTimeConfig.INSTITUTION_CLOCK) Clock clock) {
    this.configurations = configurations;
    this.bookings = bookings;
    this.instruments = instruments;
    this.accessManager = accessManager;
    this.featureFlags = featureFlags;
    this.events = events;
    this.clock = clock;
  }

  /** Returns the narrow archived document to an Owner or Manager. */
  public Summary summary(Long id, User subject) {
    requireFeatureRead(subject);
    BookingConfiguration configuration =
        configurations.findArchivedById(id).orElseThrow(NotFoundException::new);
    ResolvedResourceAccess access = requireReadable(configuration, subject);
    if (!access.hasCapability(BookingResourceRoleScheme.MANAGE_ALL_EVENTS)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    Date now = Date.from(clock.instant());
    List<FutureBooking> future =
        bookings.findFutureConfirmedByConfiguration(id, now).stream()
            .map(
                booking ->
                    new FutureBooking(
                        booking.getId(),
                        booking.getStartTime(),
                        booking.getEndTime(),
                        booking.getVersion(),
                        false,
                        true))
            .toList();
    return new Summary(
        configuration.getId(),
        configuration.getConfigurationVersion(),
        target(configuration.getTarget()),
        access.hasCapability(BookingResourceRoleScheme.ARCHIVE_CONFIGURATION),
        true,
        false,
        future);
  }

  /** Restores an archived configuration without recreating revoked calendar subscriptions. */
  public BookingConfiguration unarchive(Long id, long expectedVersion, User subject, User actor) {
    requireFeatureMutation(subject);
    BookingConfiguration configuration =
        configurations.lockArchivedById(id).orElseThrow(NotFoundException::new);
    ResolvedResourceAccess access = requireReadable(configuration, subject);
    if (!access.hasCapability(BookingResourceRoleScheme.ARCHIVE_CONFIGURATION)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    if (configuration.getConfigurationVersion() != expectedVersion) {
      throw new BookingConcurrentModificationException();
    }
    configuration.setDeleted(false);
    configuration.setUpdatedAt(Date.from(clock.instant()));
    configuration.setUpdatedBy(actor);
    BookingConfiguration saved = configurations.saveAndFlush(configuration);
    events.publishEvent(
        new BookingConfigurationAuditEvent(actor, subject, saved, AuditAction.RESTORE));
    return saved;
  }

  private ResolvedResourceAccess requireReadable(BookingConfiguration configuration, User subject) {
    ResolvedResourceAccess access =
        accessManager.resolve(configuration.getResourceAccess(), subject);
    if (!access.hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
      throw new NotFoundException();
    }
    return access;
  }

  private Target target(BookableTargetReference reference) {
    if (reference == null) {
      return null;
    }
    return instruments
        .getSafeNull(reference.id())
        .filter(instrument -> !instrument.isDeleted())
        .map(Instrument.class::cast)
        .map(instrument -> new Target(instrument.getGlobalIdentifier(), instrument.getName()))
        .orElse(null);
  }

  private void requireFeatureRead(User subject) {
    if (subject == null || !featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new NotFoundException();
    }
  }

  private void requireFeatureMutation(User subject) {
    if (subject == null || !featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }
}
