package com.researchspace.booking.service;

import static com.researchspace.model.booking.BookingSchedulingSettings.MAX_BOOKING_DURATION_MINUTES;

import com.researchspace.booking.config.BookingTimeConfig;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingPrivacy;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.service.resourceaccess.ResourceRoleScheme;
import jakarta.persistence.OptimisticLockException;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/** Transactional policy module for one-off time-slot bookings. */
@Service("timeSlotBookingManager")
public class TimeSlotBookingManagerImpl implements TimeSlotBookingManager {

  private final TimeSlotBookingDao bookingDao;
  private final BookingConfigurationDao configurationDao;
  private final BookingSchedulingPolicy schedulingPolicy;
  private final BookingMaintenancePolicy maintenancePolicy;
  private final InstrumentDao instrumentDao;
  private final ObjectProvider<ResourceRegistry> resourceRegistry;
  private final ApplicationEventPublisher events;
  private final ResourceAccessManager accessManager;
  private final CollectionDescription<TimeSlotBooking> bookingDescription;
  private final CollectionDescription<BookingConfiguration> configurationDescription;
  private final Clock clock;

  @Autowired
  public TimeSlotBookingManagerImpl(
      @Qualifier("timeSlotBookingDao") TimeSlotBookingDao bookingDao,
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao,
      BookingSchedulingPolicy schedulingPolicy,
      BookingMaintenancePolicy maintenancePolicy,
      InstrumentDao instrumentDao,
      ObjectProvider<ResourceRegistry> resourceRegistry,
      ApplicationEventPublisher events,
      ResourceAccessManager accessManager,
      @Qualifier(
              com.researchspace.booking.config.BookingResourceAccessConfiguration
                  .TIME_SLOT_BOOKING_DESCRIPTION)
          CollectionDescription<TimeSlotBooking> bookingDescription,
      @Qualifier(
              com.researchspace.booking.config.BookingResourceAccessConfiguration
                  .BOOKING_CONFIGURATION_DESCRIPTION)
          CollectionDescription<BookingConfiguration> configurationDescription,
      @Qualifier(BookingTimeConfig.INSTITUTION_CLOCK) Clock clock) {
    this.bookingDao = bookingDao;
    this.configurationDao = configurationDao;
    this.schedulingPolicy = schedulingPolicy;
    this.maintenancePolicy = maintenancePolicy;
    this.instrumentDao = instrumentDao;
    this.resourceRegistry = resourceRegistry;
    this.events = events;
    this.accessManager = accessManager;
    this.bookingDescription = bookingDescription;
    this.configurationDescription = configurationDescription;
    this.clock = clock;
  }

  TimeSlotBookingManagerImpl(
      TimeSlotBookingDao bookingDao,
      BookingConfigurationDao configurationDao,
      BookingSchedulingPolicy schedulingPolicy,
      BookingMaintenancePolicy maintenancePolicy,
      InstrumentDao instrumentDao,
      ObjectProvider<ResourceRegistry> resourceRegistry,
      ApplicationEventPublisher events,
      ResourceAccessManager accessManager,
      CollectionDescription<TimeSlotBooking> bookingDescription,
      CollectionDescription<BookingConfiguration> configurationDescription) {
    this(
        bookingDao,
        configurationDao,
        schedulingPolicy,
        maintenancePolicy,
        instrumentDao,
        resourceRegistry,
        events,
        accessManager,
        bookingDescription,
        configurationDescription,
        Clock.systemUTC());
  }

  @Override
  public ResourcePage<TimeSlotBooking> getBookings(ResourceRequest request, User actor) {
    requireAuthenticated(actor);
    ResourcePage<TimeSlotBooking> page =
        bookingDao.getReadableResources(
            authorizeRead(bookingDescription, request, actor), targetAccess(actor));
    prepare(page.resources(), actor);
    return page;
  }

  @Override
  public long countBookings(ResourceRequest request, User actor) {
    requireAuthenticated(actor);
    return bookingDao.countReadableResources(
        authorizeRead(bookingDescription, request, actor), targetAccess(actor));
  }

  @Override
  public Optional<TimeSlotBooking> getBooking(Long id, User actor) {
    requireAuthenticated(actor);
    Optional<TimeSlotBooking> booking =
        bookingDao
            .getReadableResources(
                authorizeRead(bookingDescription, idRequest(id), actor), targetAccess(actor))
            .resources()
            .stream()
            .findFirst();
    booking.ifPresent(value -> prepare(List.of(value), actor));
    return booking;
  }

  @Override
  public Optional<TimeSlotBooking> getBookingForAudit(Long id, User actor) {
    requireAuthenticated(actor);
    return bookingDao.findReadableForAuditById(
        authorizeRead(bookingDescription, idRequest(id), actor), targetAccess(actor));
  }

  @Override
  public void requireCanViewAudit(TimeSlotBooking booking, User actor) {
    requireAuthenticated(actor);
    requireCapability(
        booking.getBookingConfiguration(), actor, BookingResourceRoleScheme.VIEW_AUDIT);
  }

  @Override
  public Optional<CalendarSource> getCalendarSource(
      Long configurationId, User actor, Date refreshedAt, int maxEvents) {
    requireAuthenticated(actor);
    Objects.requireNonNull(refreshedAt, "Calendar refresh time");
    if (maxEvents < 1) {
      throw new IllegalArgumentException("Calendar event limit must be positive");
    }

    Optional<BookingConfiguration> configuration =
        findReadableConfiguration(configurationId, actor);
    if (configuration.isEmpty()) {
      return Optional.empty();
    }
    BookableTargetReference target = configuration.get().getTarget();
    if (target == null || target.type() != BookableTargetType.INSTRUMENT) {
      return Optional.empty();
    }
    InstrumentReadSummary instrument =
        instrumentDao.getBookingSummaries(Set.of(target.id())).get(target.id());
    if (instrument == null || instrument.deleted()) {
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
        new CalendarSource(instrument.name(), configuration.get().getTimeZone(), calendarEvents));
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
    requireInstrument(create.target());
    validateWindow(create.start(), create.end());
    validatePurpose(create.purpose());
    BookingConfiguration configuration =
        configurationDao
            .lockActiveByTarget(create.target().reference())
            .filter(BookingConfiguration::isEnabled)
            .orElseThrow(BookingTargetUnavailableException::new);
    requireCapability(
        configuration,
        subject,
        create.kind() == BookingEventKind.MAINTENANCE
            ? BookingResourceRoleScheme.CREATE_BLOCKOUT
            : BookingResourceRoleScheme.CREATE_BOOKING);
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
    return updateBooking(id, patch, null, subject, actor);
  }

  @Override
  public Optional<TimeSlotBooking> updateBooking(
      Long id, Patch patch, long expectedVersion, User subject, User actor) {
    return updateBooking(id, patch, Long.valueOf(expectedVersion), subject, actor);
  }

  private Optional<TimeSlotBooking> updateBooking(
      Long id, Patch patch, Long expectedVersion, User subject, User actor) {
    requireAuthenticated(subject);
    Objects.requireNonNull(patch, "Patch booking command");
    if (cancellationOnly(patch)) {
      return cancelBooking(id, expectedVersion, subject, actor);
    }
    return bookingDao
        .findReadableById(id, targetAccess(subject))
        .map(
            booking -> {
              requireExpectedVersion(booking, expectedVersion);
              BookingConfiguration configuration =
                  configurationDao
                      .lockActiveById(booking.getBookingConfiguration().getId())
                      .orElseThrow(BookingTargetUnavailableException::new);
              ResolvedResourceAccess access =
                  accessManager.resolve(configuration.getResourceAccess(), subject);
              if (!access.hasCapability(ResourceRoleScheme.READ_RESOURCE_CAPABILITY)) {
                return null;
              }
              requireCanEdit(booking, access, subject);
              if (booking.getState() != BookingState.CONFIRMED) {
                throw new BookingStateTransitionException();
              }
              if (!booking.getStartTime().toInstant().isAfter(clock.instant())) {
                throw new BookingStateTransitionException();
              }
              Date start = patch.start() == null ? booking.getStartTime() : patch.start();
              Date end = patch.end() == null ? booking.getEndTime() : patch.end();
              boolean intervalChanged =
                  !start.equals(booking.getStartTime()) || !end.equals(booking.getEndTime());
              if (intervalChanged) {
                validateWindow(start, end);
              }
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
            })
        .flatMap(Optional::ofNullable);
  }

  private Optional<TimeSlotBooking> cancelBooking(
      Long id, Long expectedVersion, User subject, User actor) {
    return bookingDao
        .findReadableForAuditById(
            authorizeRead(bookingDescription, idRequest(id), subject), targetAccess(subject))
        .map(
            booking -> {
              requireExpectedVersion(booking, expectedVersion);
              Long configurationId = booking.getBookingConfiguration().getId();
              BookingConfiguration configuration =
                  configurationDao
                      .lockById(configurationId)
                      .orElseThrow(BookingTargetUnavailableException::new);
              ResolvedResourceAccess access =
                  accessManager.resolve(configuration.getResourceAccess(), subject);
              if (!access.hasCapability(ResourceRoleScheme.READ_RESOURCE_CAPABILITY)) {
                return null;
              }
              requireCanEdit(booking, access, subject);
              if (booking.getState() != BookingState.CONFIRMED
                  || !booking.getStartTime().toInstant().isAfter(clock.instant())) {
                throw new BookingStateTransitionException();
              }
              booking.setState(BookingState.CANCELLED);
              booking.setDeleted(true);
              booking.setUpdatedAt(Date.from(clock.instant()));
              booking.setUpdatedBy(actor);
              TimeSlotBooking saved = save(booking);
              events.publishEvent(
                  new TimeSlotBookingAuditEvent(actor, subject, saved, AuditAction.DELETE));
              prepare(List.of(saved), subject);
              return saved;
            })
        .flatMap(Optional::ofNullable);
  }

  private static boolean cancellationOnly(Patch patch) {
    return patch.state() == BookingState.CANCELLED
        && patch.start() == null
        && patch.end() == null
        && !patch.purposeSupplied();
  }

  private static void requireExpectedVersion(TimeSlotBooking booking, Long expectedVersion) {
    if (expectedVersion != null && booking.getVersion() != expectedVersion) {
      throw new BookingConcurrentModificationException();
    }
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

  private static void requireCanEdit(
      TimeSlotBooking booking, ResolvedResourceAccess access, User subject) {
    boolean ownBooking =
        booking.getKind() == BookingEventKind.BOOKING && subject.equals(booking.getRequester());
    boolean mayEdit =
        access.hasCapability(BookingResourceRoleScheme.MANAGE_ALL_EVENTS)
            || (ownBooking && access.hasCapability(BookingResourceRoleScheme.MANAGE_OWN_BOOKINGS));
    if (!mayEdit) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private void prepare(List<TimeSlotBooking> bookings, User actor) {
    Map<Long, com.researchspace.model.resourceaccess.ResourceAccess> accesses =
        new LinkedHashMap<>();
    bookings.forEach(
        booking -> {
          com.researchspace.model.resourceaccess.ResourceAccess access =
              booking.getBookingConfiguration().getResourceAccess();
          accesses.put(access.getId(), access);
        });
    Map<Long, ResolvedResourceAccess> resolved = accessManager.resolveAll(accesses.values(), actor);
    for (TimeSlotBooking booking : bookings) {
      booking.getRequester().getUsername();
      booking.getRequester().getFullName();
      if (booking.getCreatedBy() != null) {
        booking.getCreatedBy().getUsername();
        booking.getCreatedBy().getFullName();
      }
      ResolvedResourceAccess access =
          resolved.get(booking.getBookingConfiguration().getResourceAccess().getId());
      boolean currentRole = access.hasCapability(ResourceRoleScheme.READ_RESOURCE_CAPABILITY);
      boolean ownBooking = actor.equals(booking.getRequester());
      boolean mayManage =
          (access.hasCapability(BookingResourceRoleScheme.MANAGE_ALL_EVENTS)
              || (ownBooking
                  && access.hasCapability(BookingResourceRoleScheme.MANAGE_OWN_BOOKINGS)));
      boolean futureConfirmed =
          !booking.isDeleted()
              && booking.getState() == BookingState.CONFIRMED
              && booking.getStartTime().toInstant().isAfter(clock.instant());
      boolean canCancel = mayManage && futureConfirmed;
      booking.prepareView(
          currentRole || ownBooking ? BookingPrivacy.FULL : BookingPrivacy.BUSY,
          canCancel
              && booking.getBookingConfiguration().getState() == BookingConfigurationState.ACTIVE,
          canCancel,
          currentRole);
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

  private Optional<BookingConfiguration> findReadableConfiguration(Long id, User subject) {
    return configurationDao
        .getResources(
            authorizeRead(configurationDescription, idRequest(id), subject),
            1,
            targetAccess(subject))
        .stream()
        .findFirst();
  }

  private static <T> ResourceRequest authorizeRead(
      CollectionDescription<T> description, ResourceRequest request, User subject) {
    AccessResult access =
        description
            .accessPolicy()
            .readAccess()
            .check(
                new AccessContext(
                    subject, AccessContext.Operation.READ, description.resourceName()));
    if (access.isDenied()) {
      throw new AuthorizationException("errors.api.v2.authenticationRequired");
    }
    return access.constraintOrEmpty().map(request::restrict).orElse(request);
  }

  private void requireCapability(
      BookingConfiguration configuration, User subject, String capability) {
    if (!accessManager
        .resolve(configuration.getResourceAccess(), subject)
        .hasCapability(capability)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
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
