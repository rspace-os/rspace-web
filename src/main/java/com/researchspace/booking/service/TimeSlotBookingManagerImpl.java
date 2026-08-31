package com.researchspace.booking.service;

import static com.researchspace.model.booking.BookingSchedulingSettings.MAX_BOOKING_DURATION_MINUTES;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import jakarta.persistence.OptimisticLockException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/** Transactional policy module for one-off time-slot bookings. */
@Service("timeSlotBookingManager")
public class TimeSlotBookingManagerImpl implements TimeSlotBookingManager {

  private final TimeSlotBookingDao bookingDao;
  private final BookingConfigurationDao configurationDao;
  private final InventoryPermissionUtils inventoryPermissions;
  private final BookingSchedulingPolicy schedulingPolicy;
  private final BookingMaintenancePolicy maintenancePolicy;
  private final InstrumentDao instrumentDao;
  private final ObjectProvider<ResourceRegistry> resourceRegistry;
  private final ApplicationEventPublisher events;

  public TimeSlotBookingManagerImpl(
      @Qualifier("timeSlotBookingDao") TimeSlotBookingDao bookingDao,
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao,
      InventoryPermissionUtils inventoryPermissions,
      BookingSchedulingPolicy schedulingPolicy,
      BookingMaintenancePolicy maintenancePolicy,
      InstrumentDao instrumentDao,
      ObjectProvider<ResourceRegistry> resourceRegistry,
      ApplicationEventPublisher events) {
    this.bookingDao = bookingDao;
    this.configurationDao = configurationDao;
    this.inventoryPermissions = inventoryPermissions;
    this.schedulingPolicy = schedulingPolicy;
    this.maintenancePolicy = maintenancePolicy;
    this.instrumentDao = instrumentDao;
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
  public Optional<CalendarSource> getCalendarSource(
      Long configurationId, User actor, Date refreshedAt, int maxEvents) {
    requireAuthenticated(actor);
    Objects.requireNonNull(refreshedAt, "Calendar refresh time");
    if (maxEvents < 1) {
      throw new IllegalArgumentException("Calendar event limit must be positive");
    }

    RelationshipReadAccess access = targetAccess(actor);
    Optional<BookingConfiguration> configuration =
        configurationDao.getResources(idRequest(configurationId), 1, access).stream().findFirst();
    if (configuration.isEmpty()) {
      return Optional.empty();
    }
    BookableTargetReference target = configuration.get().getTarget();
    if (target == null || target.type() != BookableTargetType.INSTRUMENT) {
      return Optional.empty();
    }
    Optional<Instrument> instrument =
        instrumentDao
            .getReadableResources(idRequest(target.id()), access.result("instruments"))
            .resources()
            .stream()
            .findFirst();
    if (instrument.isEmpty() || instrument.get().isDeleted() || instrument.get().isTemplate()) {
      return Optional.empty();
    }

    Date cutoff = Date.from(refreshedAt.toInstant().minus(30, ChronoUnit.DAYS));
    List<TimeSlotBooking> bookings =
        bookingDao.findCalendarBookings(configurationId, cutoff, maxEvents + 1);
    if (bookings.size() > maxEvents) {
      throw new CalendarSourceTooLargeException();
    }
    prepare(bookings, actor);
    List<CalendarEvent> calendarEvents =
        bookings.stream()
            .map(
                booking ->
                    new CalendarEvent(
                        booking.getId(),
                        booking.getStartTime(),
                        booking.getEndTime(),
                        booking.getCreatedAt(),
                        booking.getUpdatedAt(),
                        booking.getKind(),
                        booking.getPrivacy(),
                        booking.getVisibleBookedBy(),
                        booking.getVisibleCreatedBy(),
                        booking.getVisiblePurpose(),
                        booking.isCanEdit()))
            .toList();
    return Optional.of(
        new CalendarSource(
            instrument.get().getName(), configuration.get().getTimeZone(), calendarEvents));
  }

  @Override
  public CalendarSource getUserCalendarSource(User actor, Date refreshedAt, int maxEvents) {
    requireAuthenticated(actor);
    Objects.requireNonNull(refreshedAt, "Calendar refresh time");
    if (maxEvents < 1) {
      throw new IllegalArgumentException("Calendar event limit must be positive");
    }
    Date cutoff = Date.from(refreshedAt.toInstant().minus(30, ChronoUnit.DAYS));
    List<TimeSlotBooking> bookings =
        bookingDao.findUserCalendarBookings(actor.getId(), cutoff, maxEvents + 1);
    if (bookings.size() > maxEvents) {
      throw new CalendarSourceTooLargeException();
    }
    prepare(bookings, actor);
    Set<Long> targetIds =
        bookings.stream()
            .map(TimeSlotBooking::getBookingConfiguration)
            .map(BookingConfiguration::getTarget)
            .filter(Objects::nonNull)
            .filter(target -> target.type() == BookableTargetType.INSTRUMENT)
            .map(BookableTargetReference::id)
            .collect(java.util.stream.Collectors.toSet());
    Map<Long, String> itemNames = instrumentDao.getNamesByIds(targetIds);
    List<CalendarEvent> calendarEvents =
        bookings.stream()
            .map(
                booking -> {
                  BookableTargetReference target = booking.getBookingConfiguration().getTarget();
                  String itemName =
                      target == null || target.type() != BookableTargetType.INSTRUMENT
                          ? null
                          : itemNames.get(target.id());
                  return new CalendarEvent(
                      booking.getId(),
                      booking.getStartTime(),
                      booking.getEndTime(),
                      booking.getCreatedAt(),
                      booking.getUpdatedAt(),
                      booking.getKind(),
                      booking.getPrivacy(),
                      booking.getVisibleBookedBy(),
                      booking.getVisibleCreatedBy(),
                      booking.getVisiblePurpose(),
                      booking.isCanEdit(),
                      itemName);
                })
            .toList();
    return new CalendarSource("booking:calendar.feed.myBookings", "UTC", calendarEvents, true);
  }

  @Override
  public TimeSlotBooking createBooking(Create create, User subject, User actor) {
    requireAuthenticated(subject);
    Objects.requireNonNull(create, "Create booking command");
    Instrument instrument = requireInstrument(create.target());
    if (!inventoryPermissions.canUserReadInventoryRecord(instrument, subject)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    if (create.kind() == BookingEventKind.MAINTENANCE
        && !maintenancePolicy.canManageMaintenance(create.target().reference(), subject, actor)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    validateWindow(create.start(), create.end());
    validatePurpose(create.purpose());
    BookingConfiguration configuration =
        configurationDao
            .lockByTarget(create.target().reference())
            .filter(BookingConfiguration::isEnabled)
            .orElseThrow(BookingTargetUnavailableException::new);
    BookingSchedulingPolicy.ConflictInterval conflict =
        validateScheduling(configuration, create.kind(), create.start(), create.end());
    requireNoOverlap(
        configuration.getId(),
        conflict.start(),
        conflict.end(),
        null,
        conflictingKinds(configuration, create.kind()));

    Date now = new Date();
    TimeSlotBooking booking = new TimeSlotBooking();
    booking.setBookingConfiguration(configuration);
    booking.setRequester(subject);
    booking.setKind(create.kind());
    booking.setStartTime(create.start());
    booking.setEndTime(create.end());
    booking.setState(BookingState.CONFIRMED);
    booking.setPurpose(create.purpose());
    booking.setDeleted(false);
    booking.setCreatedAt(now);
    booking.setUpdatedAt(now);
    booking.setCreatedBy(actor);
    booking.setUpdatedBy(actor);
    TimeSlotBooking saved = save(booking);
    events.publishEvent(new TimeSlotBookingAuditEvent(actor, subject, saved, AuditAction.CREATE));
    prepare(List.of(saved), subject);
    return saved;
  }

  @Override
  public Optional<TimeSlotBooking> updateBooking(Long id, Patch patch, User subject, User actor) {
    requireAuthenticated(subject);
    Objects.requireNonNull(patch, "Patch booking command");
    return bookingDao
        .findReadableById(id, targetAccess(subject))
        .map(
            booking -> {
              requireCanEdit(booking, subject, actor);
              if (booking.getState() != BookingState.CONFIRMED) {
                throw new BookingStateTransitionException();
              }
              Date start = patch.start() == null ? booking.getStartTime() : patch.start();
              Date end = patch.end() == null ? booking.getEndTime() : patch.end();
              boolean intervalChanged =
                  !start.equals(booking.getStartTime()) || !end.equals(booking.getEndTime());
              if (intervalChanged) {
                validateWindow(start, end);
              }
              BookingConfiguration configuration =
                  configurationDao
                      .lockById(booking.getBookingConfiguration().getId())
                      .orElseThrow(BookingTargetUnavailableException::new);
              if (intervalChanged) {
                if (!configuration.isEnabled()) {
                  throw new BookingTargetUnavailableException();
                }
                BookingSchedulingPolicy.ConflictInterval conflict =
                    validateScheduling(configuration, booking.getKind(), start, end);
                requireNoOverlap(
                    configuration.getId(),
                    conflict.start(),
                    conflict.end(),
                    booking.getId(),
                    conflictingKinds(configuration, booking.getKind()));
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
              TimeSlotBooking saved = save(booking);
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

  private void requireCanEdit(TimeSlotBooking booking, User subject, User actor) {
    BookableTargetReference target = booking.getBookingConfiguration().getTarget();
    if (booking.getKind() == BookingEventKind.MAINTENANCE) {
      if (!maintenancePolicy.canManageMaintenance(target, subject, actor)) {
        throw new AuthorizationException("errors.api.v2.forbidden");
      }
      return;
    }
    boolean requester = subject.equals(booking.getRequester());
    boolean owner =
        bookingDao
            .findOwnedInstrumentIds(Set.of(target.id()), subject.getId())
            .contains(target.id());
    if (!requester && !owner && !subject.hasSysadminRole()) {
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
      if (booking.getCreatedBy() != null) {
        booking.getCreatedBy().getUsername();
        booking.getCreatedBy().getFullName();
      }
      Long targetId = booking.getBookingConfiguration().getTarget().id();
      boolean canEdit;
      if (booking.getKind() == BookingEventKind.MAINTENANCE) {
        canEdit =
            maintenancePolicy.canManageMaintenance(
                booking.getBookingConfiguration().getTarget(), actor, actor);
      } else {
        canEdit =
            actor.hasSysadminRole()
                || actor.equals(booking.getRequester())
                || owned.contains(targetId);
      }
      booking.prepareView(BookingPrivacy.FULL, canEdit);
    }
  }

  private void requireNoOverlap(
      Long configurationId,
      Date start,
      Date end,
      Long excludedId,
      Set<BookingEventKind> includedKinds) {
    if (bookingDao.overlaps(configurationId, start, end, excludedId, includedKinds)) {
      throw new BookingOverlapException();
    }
  }

  private BookingSchedulingPolicy.ConflictInterval validateScheduling(
      BookingConfiguration configuration, BookingEventKind kind, Date start, Date end) {
    return kind == BookingEventKind.MAINTENANCE
        ? schedulingPolicy.validateMaintenance(configuration, start, end)
        : schedulingPolicy.validate(configuration, start, end);
  }

  private static Set<BookingEventKind> conflictingKinds(
      BookingConfiguration configuration, BookingEventKind kind) {
    if (kind == BookingEventKind.BOOKING && configuration.isAllowDoubleBooking()) {
      return Set.of(BookingEventKind.MAINTENANCE);
    }
    return Set.of(BookingEventKind.BOOKING, BookingEventKind.MAINTENANCE);
  }

  private TimeSlotBooking save(TimeSlotBooking booking) {
    try {
      return bookingDao.saveAndFlush(booking);
    } catch (OptimisticLockException | ObjectOptimisticLockingFailureException exception) {
      throw new BookingConcurrentModificationException();
    }
  }

  private static void validateWindow(Date start, Date end) {
    if (start == null || end == null || !end.after(start)) {
      throw new BookingWindowException();
    }
    if (Duration.between(start.toInstant(), end.toInstant())
            .compareTo(Duration.ofMinutes(MAX_BOOKING_DURATION_MINUTES))
        > 0) {
      throw new BookingDurationException();
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

  private static ResourceRequest idRequest(Long id) {
    return new ResourceRequest(
        new FilterExpression.Comparison("id", Operator.EQUAL, List.of(id), false),
        List.of(),
        new ResourceRequest.Page(1, 1),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private static void requireAuthenticated(User actor) {
    if (actor == null) {
      throw new AuthorizationException("errors.api.v2.authenticationRequired");
    }
  }
}
