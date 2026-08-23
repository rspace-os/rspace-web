package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Transactional policy module for one-off time-slot bookings. */
@Service("timeSlotBookingManager")
public class TimeSlotBookingManagerImpl implements TimeSlotBookingManager {

  private final TimeSlotBookingDao bookingDao;
  private final BookingConfigurationDao configurationDao;
  private final InventoryPermissionUtils inventoryPermissions;
  private final ObjectProvider<ResourceRegistry> resourceRegistry;
  private final ApplicationEventPublisher events;

  public TimeSlotBookingManagerImpl(
      @Qualifier("timeSlotBookingDao") TimeSlotBookingDao bookingDao,
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao,
      InventoryPermissionUtils inventoryPermissions,
      ObjectProvider<ResourceRegistry> resourceRegistry,
      ApplicationEventPublisher events) {
    this.bookingDao = bookingDao;
    this.configurationDao = configurationDao;
    this.inventoryPermissions = inventoryPermissions;
    this.resourceRegistry = resourceRegistry;
    this.events = events;
  }

  @Override
  public ResourcePage<TimeSlotBooking> getBookings(ResourceRequest request, User actor) {
    requireAuthenticated(actor);
    ResourcePage<TimeSlotBooking> page =
        bookingDao.getReadableResources(request, targetAccess(actor));
    prepare(page.resources(), actor);
    return page;
  }

  @Override
  public long countBookings(ResourceRequest request, User actor) {
    requireAuthenticated(actor);
    return bookingDao.countReadableResources(request, targetAccess(actor));
  }

  @Override
  public Optional<TimeSlotBooking> getBooking(Long id, User actor) {
    requireAuthenticated(actor);
    Optional<TimeSlotBooking> booking = bookingDao.findReadableById(id, targetAccess(actor));
    booking.ifPresent(value -> prepare(List.of(value), actor));
    return booking;
  }

  @Override
  public TimeSlotBooking createBooking(Create create, User subject, User actor) {
    requireAuthenticated(subject);
    Objects.requireNonNull(create, "Create booking command");
    Instrument instrument = requireInstrument(create.target());
    if (!inventoryPermissions.canUserReadInventoryRecord(instrument, subject)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    validateWindow(create.start(), create.end());
    validatePurpose(create.purpose());
    BookingConfiguration configuration =
        configurationDao
            .lockByTarget(create.target().reference())
            .filter(BookingConfiguration::isEnabled)
            .orElseThrow(BookingTargetUnavailableException::new);
    requireNoOverlap(configuration.getId(), create.start(), create.end(), null);

    Date now = new Date();
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setBookingConfiguration(configuration);
    booking.setRequester(subject);
    booking.setStartTime(create.start());
    booking.setEndTime(create.end());
    booking.setState(BookingState.CONFIRMED);
    booking.setPurpose(create.purpose());
    booking.setDeleted(false);
    booking.setCreatedAt(now);
    booking.setUpdatedAt(now);
    booking.setCreatedBy(actor);
    booking.setUpdatedBy(actor);
    TimeSlotBooking saved = bookingDao.saveAndFlush(booking);
    events.publishEvent(new TimeSlotBookingAuditEvent(actor, subject, saved, AuditAction.CREATE));
    prepare(List.of(saved), subject);
    return saved;
  }

  @Override
  public Optional<TimeSlotBooking> updateBooking(Long id, Patch patch, User subject, User actor) {
    requireAuthenticated(subject);
    Objects.requireNonNull(patch, "Patch booking command");
    return bookingDao
        .getSafeNull(id)
        .map(
            booking -> {
              requireCanEdit(booking, subject);
              if (booking.getState() != BookingState.CONFIRMED) {
                throw new BookingStateTransitionException();
              }
              BookingConfiguration configuration =
                  configurationDao
                      .lockById(booking.getBookingConfiguration().getId())
                      .orElseThrow(BookingTargetUnavailableException::new);
              Date start = patch.start() == null ? booking.getStartTime() : patch.start();
              Date end = patch.end() == null ? booking.getEndTime() : patch.end();
              boolean intervalChanged =
                  !start.equals(booking.getStartTime()) || !end.equals(booking.getEndTime());
              if (intervalChanged) {
                if (!configuration.isEnabled()) {
                  throw new BookingTargetUnavailableException();
                }
                validateWindow(start, end);
                requireNoOverlap(configuration.getId(), start, end, booking.getId());
              }
              if (patch.purposeSupplied()) {
                validatePurpose(patch.purpose());
                booking.setPurpose(patch.purpose());
              }
              if (patch.state() != null) {
                if (patch.state() != BookingState.CANCELLED
                    || booking.getState() != BookingState.CONFIRMED) {
                  throw new BookingStateTransitionException();
                }
                booking.setState(BookingState.CANCELLED);
                booking.setDeleted(true);
              }
              booking.setStartTime(start);
              booking.setEndTime(end);
              booking.setUpdatedAt(new Date());
              booking.setUpdatedBy(actor);
              TimeSlotBooking saved = bookingDao.saveAndFlush(booking);
              AuditAction action =
                  saved.getState() == BookingState.CANCELLED
                      ? AuditAction.DELETE
                      : AuditAction.WRITE;
              events.publishEvent(new TimeSlotBookingAuditEvent(actor, subject, saved, action));
              prepare(List.of(saved), subject);
              return saved;
            });
  }

  private static Instrument requireInstrument(ResolvedBookableTarget target) {
    if (target == null
        || target.reference().type() != BookableTargetType.INSTRUMENT
        || !(target.entity() instanceof Instrument instrument)
        || instrument.isTemplate()
        || instrument.isDeleted()
        || !target.reference().id().equals(instrument.getId())) {
      throw new InvalidBookableTargetException();
    }
    return instrument;
  }

  private void requireCanEdit(TimeSlotBooking booking, User actor) {
    BookableTargetReference target = booking.getBookingConfiguration().getTarget();
    boolean requester = actor.equals(booking.getRequester());
    boolean owner =
        bookingDao.findOwnedInstrumentIds(Set.of(target.id()), actor.getId()).contains(target.id());
    if (!requester && !owner && !actor.hasSysadminRole()) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private void prepare(List<TimeSlotBooking> bookings, User actor) {
    Set<Long> targetIds = new HashSet<>();
    for (TimeSlotBooking booking : bookings) {
      targetIds.add(booking.getBookingConfiguration().getTarget().id());
    }
    Set<Long> owned = bookingDao.findOwnedInstrumentIds(targetIds, actor.getId());
    for (TimeSlotBooking booking : bookings) {
      booking.getRequester().getUsername();
      booking.getRequester().getFullName();
      Long targetId = booking.getBookingConfiguration().getTarget().id();
      boolean canEdit =
          actor.hasSysadminRole()
              || actor.equals(booking.getRequester())
              || owned.contains(targetId);
      booking.prepareView(canEdit ? BookingPrivacy.FULL : BookingPrivacy.BUSY, canEdit);
    }
  }

  private void requireNoOverlap(Long configurationId, Date start, Date end, Long excludedId) {
    if (bookingDao.overlaps(configurationId, start, end, excludedId)) {
      throw new BookingOverlapException();
    }
  }

  private static void validateWindow(Date start, Date end) {
    if (start == null || end == null || !end.after(start)) {
      throw new BookingWindowException();
    }
  }

  private static void validatePurpose(String purpose) {
    if (purpose != null && purpose.length() > 1000) {
      throw new IllegalArgumentException("errors.api.v2.booking.purpose.length");
    }
  }

  private RelationshipReadAccess targetAccess(User actor) {
    return RelationshipReadAccess.forActor(resourceRegistry.getObject(), actor);
  }

  private static void requireAuthenticated(User actor) {
    if (actor == null) {
      throw new AuthorizationException("errors.api.v2.authenticationRequired");
    }
  }
}
