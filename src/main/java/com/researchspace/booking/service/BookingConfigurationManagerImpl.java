package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.AuditDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationCapabilities;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceRoleScheme;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Shared domain module for reading and changing booking configurations. */
@Service("bookingConfigurationManager")
public class BookingConfigurationManagerImpl implements BookingConfigurationManager {

  @Autowired private AuditDao auditDao;

  private final BookingConfigurationDao bookingConfigurationDao;
  private final BookingConfigurationDefaultsDao defaultsDao;
  private final InstrumentDao instrumentDao;
  private final Validator validator;
  private final ApplicationEventPublisher events;
  private final ObjectProvider<ResourceRegistry> resourceRegistry;
  private final CollectionDescription<BookingConfiguration> description;
  private final BookingItemPermissions itemPermissions;
  private final BookingCalendarSubscriptionDao calendarSubscriptions;
  private final TimeSlotBookingDao timeSlotBookings;
  private final BookingNotificationService bookingNotificationService;

  @Autowired
  public BookingConfigurationManagerImpl(
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao bookingConfigurationDao,
      @Qualifier("bookingConfigurationDefaultsDao") BookingConfigurationDefaultsDao defaultsDao,
      InstrumentDao instrumentDao,
      Validator validator,
      ApplicationEventPublisher events,
      ObjectProvider<ResourceRegistry> resourceRegistry,
      @Qualifier(
              com.researchspace.booking.config.BookingResourceAccessConfiguration
                  .BOOKING_CONFIGURATION_DESCRIPTION)
          CollectionDescription<BookingConfiguration> description,
      BookingItemPermissions itemPermissions,
      @Qualifier("bookingCalendarSubscriptionDao")
          BookingCalendarSubscriptionDao calendarSubscriptions,
      @Qualifier("timeSlotBookingDao") TimeSlotBookingDao timeSlotBookings,
      BookingNotificationService bookingNotificationService) {
    this.bookingConfigurationDao = bookingConfigurationDao;
    this.defaultsDao = defaultsDao;
    this.instrumentDao = instrumentDao;
    this.validator = validator;
    this.events = events;
    this.resourceRegistry = resourceRegistry;
    this.description = description;
    this.itemPermissions = itemPermissions;
    this.calendarSubscriptions = calendarSubscriptions;
    this.timeSlotBookings = timeSlotBookings;
    this.bookingNotificationService = bookingNotificationService;
  }

  /** Returns one page selected by a parsed collection request. */
  @Override
  public ResourcePage<BookingConfiguration> getConfigurations(ResourceRequest request, User actor) {
    return getConfigurations(request, actor, null);
  }

  @Override
  public ResourcePage<BookingConfiguration> getConfigurations(
      ResourceRequest request,
      User actor,
      com.researchspace.dao.query.RsqlCollectionQuery.Predicate restriction) {
    ResourcePage<BookingConfiguration> page =
        restriction == null
            ? bookingConfigurationDao.getResources(
                authorizeRead(request, actor), targetAccess(actor))
            : bookingConfigurationDao.getCalendarResources(
                authorizeRead(request, actor), targetAccess(actor), restriction);
    prepareAccessProjection(page.resources(), actor);
    return page;
  }

  /** Counts configurations selected by a parsed collection request. */
  @Override
  public long countConfigurations(ResourceRequest request, User actor) {
    return bookingConfigurationDao.countResources(
        authorizeRead(request, actor), targetAccess(actor));
  }

  /** Finds one configuration without throwing when it is absent. */
  @Override
  public Optional<BookingConfiguration> getConfiguration(Long id, User actor) {
    Optional<BookingConfiguration> configuration =
        bookingConfigurationDao
            .getResources(authorizeRead(idRequest(id), actor), 1, targetAccess(actor))
            .stream()
            .findFirst();
    configuration.ifPresent(value -> prepareAccessProjection(List.of(value), actor));
    return configuration;
  }

  @Override
  public Optional<BookingConfiguration> getConfigurationForAudit(Long id, User actor) {
    Optional<BookingConfiguration> current = getConfiguration(id, actor);
    if (current.isPresent()
        || !actor.hasSysadminRole()
        || bookingConfigurationDao.getSafeNull(id).isPresent()) {
      return current;
    }
    return Optional.ofNullable(auditDao.getNewestRevisionForEntity(BookingConfiguration.class, id))
        .map(AuditedEntity::getEntity);
  }

  private ResourceRequest authorizeRead(ResourceRequest request, User actor) {
    AccessResult access =
        description
            .accessPolicy()
            .readAccess()
            .check(
                new AccessContext(actor, AccessContext.Operation.READ, description.resourceName()));
    if (access.isDenied()) {
      throw new AuthorizationException("errors.api.v2.authenticationRequired");
    }
    return access.constraintOrEmpty().map(request::restrict).orElse(request);
  }

  private RelationshipReadAccess targetAccess(User actor) {
    return RelationshipReadAccess.forActor(resourceRegistry.getObject(), actor);
  }

  @Override
  public BookingConfiguration createConfiguration(Create create, User subject, User actor) {
    return createConfigurations(List.of(create), subject, actor).get(0);
  }

  @Override
  public List<BookingConfiguration> createConfigurations(
      List<Create> creates, User subject, User actor) {
    requireAuthenticated(subject);
    if (creates.size() > ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkCreateRows()) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }
    BookingConfigurationDefaults defaults =
        defaultsDao
            .getSafeNull(BookingConfigurationDefaults.SINGLETON_ID)
            .orElseThrow(
                () -> new IllegalStateException("Booking configuration defaults row is missing"));
    Date now = new Date();
    List<BookingConfiguration> configurations =
        creates.stream()
            .map(create -> configuration(create, defaults, subject, actor, now))
            .toList();
    configurations.forEach(configuration -> initializeAudit(configuration, actor, now));
    Set<BookableTargetReference> targets = new HashSet<>();
    for (BookingConfiguration configuration : configurations) {
      if (!targets.add(configuration.getTarget())) {
        throw new BookingConfigurationTargetConflictException();
      }
      requireTargetAvailable(configuration.getTarget(), null);
    }
    List<BookingConfiguration> saved = configurations.stream().map(this::save).toList();
    saved.forEach(configuration -> notifyAudit(actor, subject, configuration, AuditAction.CREATE));
    prepareAccessProjection(saved, subject);
    return saved;
  }

  private BookingConfiguration configuration(
      Create create,
      BookingConfigurationDefaults defaults,
      User subject,
      User actor,
      Date timestamp) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setEnabled(create.enabled());
    configuration.setState(BookingConfigurationState.ACTIVE);
    configuration.setTimeZone(create.timeZone());
    create
        .schedulingSettings()
        .merge(BookingSchedulingSettings.from(defaults))
        .applyTo(configuration);
    BookableTargetReference target = validateTarget(create.target());
    Instrument instrument =
        instrumentDao.lockById(target.id()).orElseThrow(InvalidBookableTargetException::new);
    validateLockedTarget(target, instrument);
    configuration.replaceTarget(target);
    requireCapability(configuration, subject, BookingResourceRoleScheme.EDIT_CONFIGURATION);
    // Booking access is inherited from the current Inventory target. New configurations do not
    // create an independent ResourceAccess aggregate or copy Booking sharing defaults.
    configuration.setResourceAccess(null);
    validateSettings(configuration);
    validate(configuration);
    return configuration;
  }

  @Override
  public Optional<BookingConfiguration> updateConfiguration(
      Long id, Patch patch, User subject, User actor) {
    return updateConfiguration(id, patch, null, subject, actor);
  }

  @Override
  public Optional<BookingConfiguration> updateConfiguration(
      Long id, Patch patch, long expectedVersion, User subject, User actor) {
    return updateConfiguration(id, patch, Long.valueOf(expectedVersion), subject, actor);
  }

  private Optional<BookingConfiguration> updateConfiguration(
      Long id, Patch patch, Long expectedVersion, User subject, User actor) {
    requireAuthenticated(subject);
    Optional<BookingConfiguration> updated =
        bookingConfigurationDao
            .lockById(id)
            .filter(configuration -> canRead(configuration, subject))
            .map(
                configuration -> {
                  if (expectedVersion != null
                      && configuration.getConfigurationVersion() != expectedVersion) {
                    throw new BookingConcurrentModificationException();
                  }
                  requireCapability(
                      configuration, subject, BookingResourceRoleScheme.EDIT_CONFIGURATION);
                  validatePatchForState(patch, configuration);
                  if (unchanged(patch, configuration)) {
                    return configuration;
                  }
                  boolean restoring = patch.state() == BookingConfigurationState.ACTIVE;
                  apply(patch, configuration);
                  touchAudit(configuration, actor, new Date());
                  validateSettings(configuration);
                  validate(configuration);
                  BookingConfiguration saved = save(configuration);
                  notifyAudit(
                      actor, subject, saved, restoring ? AuditAction.RESTORE : AuditAction.WRITE);
                  return saved;
                });
    updated.ifPresent(value -> prepareAccessProjection(List.of(value), subject));
    return updated;
  }

  @Override
  public List<BookingConfiguration> updateConfigurations(
      ResourceRequest request, Patch patch, User subject, User actor) {
    if (patch.state() != null) {
      throw new BookingConfigurationLifecycleException();
    }
    List<BookingConfiguration> matches = bulkMatches(request, subject);
    if (matches.stream()
        .anyMatch(configuration -> configuration.getState() != BookingConfigurationState.ACTIVE)) {
      throw new BookingConfigurationLifecycleException();
    }
    Date now = new Date();
    matches.forEach(
        configuration -> {
          apply(patch, configuration);
          touchAudit(configuration, actor, now);
          validateSettings(configuration);
          validate(configuration);
        });
    matches.forEach(this::save);
    matches.forEach(configuration -> notifyAudit(actor, subject, configuration, AuditAction.WRITE));
    prepareAccessProjection(matches, subject);
    return matches;
  }

  @Override
  public Optional<BookingConfiguration> archiveConfiguration(
      Long id, long expectedVersion, User subject, User actor) {
    requireAuthenticated(subject);
    Optional<BookingConfiguration> configuration =
        bookingConfigurationDao.lockById(id).filter(value -> canRead(value, subject));
    if (configuration.isEmpty()) {
      return Optional.empty();
    }
    BookingConfiguration existing = configuration.orElseThrow();
    if (existing.getConfigurationVersion() != expectedVersion) {
      throw new BookingConcurrentModificationException();
    }
    requireCapability(existing, subject, BookingResourceRoleScheme.EDIT_CONFIGURATION);
    if (existing.getState() == BookingConfigurationState.ARCHIVED) {
      prepareAccessProjection(List.of(existing), subject);
      return configuration;
    }
    BookingConfiguration archived = archive(existing, subject, actor);
    notifyAudit(actor, subject, archived, AuditAction.DELETE);
    prepareAccessProjection(List.of(archived), subject);
    return Optional.of(archived);
  }

  @Override
  public Optional<Long> permanentlyDeleteConfiguration(
      Long id, long expectedVersion, User subject, User actor) {
    requireAuthenticated(subject);
    requireDirectSysadmin(subject, actor);
    Optional<BookingConfiguration> found = bookingConfigurationDao.lockById(id);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    BookingConfiguration configuration = found.orElseThrow();
    if (configuration.getConfigurationVersion() != expectedVersion) {
      throw new BookingConcurrentModificationException();
    }
    int assignmentCount =
        configuration.getResourceAccess() == null
            ? 0
            : configuration.getResourceAccess().getAssignments().size();
    String targetName =
        instrumentDao
            .getSafeNull(configuration.getTarget().id())
            .map(Instrument::getName)
            .orElse("Unavailable target");
    int bookingCount = timeSlotBookings.removeAllByConfigurationId(id);
    int subscriptionCount = calendarSubscriptions.deleteByConfigurationId(id);
    BookingConfigurationPermanentDeleteSnapshot snapshot =
        new BookingConfigurationPermanentDeleteSnapshot(
            id,
            configuration.getConfigurationVersion(),
            configuration.getTarget(),
            targetName,
            configuration.getState(),
            bookingCount,
            subscriptionCount,
            assignmentCount,
            Instant.now());
    bookingConfigurationDao.removeConfigurationAndAccess(configuration);
    events.publishEvent(
        new BookingConfigurationPermanentDeleteAuditEvent(actor, subject, snapshot));
    return Optional.of(id);
  }

  @Override
  public List<BookingConfiguration> archiveConfigurations(
      ResourceRequest request, User subject, User actor) {
    List<BookingConfiguration> matches = bulkMatches(request, subject);
    Date now = new Date();
    List<BookingConfiguration> active =
        matches.stream()
            .filter(configuration -> configuration.getState() == BookingConfigurationState.ACTIVE)
            .toList();
    active.forEach(configuration -> archive(configuration, subject, actor, now));
    active.forEach(configuration -> notifyAudit(actor, subject, configuration, AuditAction.DELETE));
    prepareAccessProjection(matches, subject);
    return matches;
  }

  private static void requireAuthenticated(User actor) {
    if (actor == null || !actor.isEnabled()) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static void requireSysadmin(User actor) {
    if (actor == null || !actor.hasSysadminRole()) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static void requireDirectSysadmin(User subject, User actor) {
    if (subject == null
        || actor == null
        || !subject.hasSysadminRole()
        || !actor.hasSysadminRole()
        || !Objects.equals(subject.getId(), actor.getId())) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private void validate(BookingConfiguration configuration) {
    Set<ConstraintViolation<BookingConfiguration>> violations = validator.validate(configuration);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  private List<BookingConfiguration> bulkMatches(ResourceRequest request, User actor) {
    requireSysadmin(actor);
    if (request.filter() == null) {
      throw new CollectionMutationException(CollectionMutationException.Reason.FILTER_REQUIRED);
    }
    List<BookingConfiguration> matches =
        bookingConfigurationDao.lockResources(
            request,
            ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkUpdateDeleteRows() + 1,
            RelationshipReadAccess.unrestricted(resourceRegistry.getObject()));
    if (matches.size()
        > ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkUpdateDeleteRows()) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }
    return matches;
  }

  private static void apply(Patch patch, BookingConfiguration configuration) {
    if (patch.enabled() != null) {
      configuration.setEnabled(patch.enabled());
    }
    if (patch.timeZone() != null) {
      configuration.setTimeZone(patch.timeZone());
    }
    patch
        .schedulingSettings()
        .merge(BookingSchedulingSettings.from(configuration))
        .applyTo(configuration);
    if (patch.state() != null) {
      configuration.setState(patch.state());
    }
  }

  private static boolean unchanged(Patch patch, BookingConfiguration configuration) {
    return patch.enabled() == null
        && patch.timeZone() == null
        && patch.schedulingSettings().isEmpty()
        && (patch.state() == null
            || (patch.state() == BookingConfigurationState.ACTIVE
                && configuration.getState() == BookingConfigurationState.ACTIVE));
  }

  private static void validatePatchForState(Patch patch, BookingConfiguration configuration) {
    if (patch.state() == BookingConfigurationState.ARCHIVED) {
      throw new BookingConfigurationLifecycleException();
    }
    if (configuration.getState() == BookingConfigurationState.ACTIVE) {
      return;
    }
    boolean stateOnlyRestore =
        patch.state() == BookingConfigurationState.ACTIVE
            && patch.enabled() == null
            && patch.timeZone() == null
            && patch.schedulingSettings().isEmpty();
    if (!stateOnlyRestore) {
      throw new BookingConfigurationLifecycleException();
    }
  }

  private static void validateSettings(BookingConfiguration configuration) {
    BookingSettingsValidation.requireValid(BookingSchedulingSettings.from(configuration));
  }

  private static void initializeAudit(
      BookingConfiguration configuration, User actor, Date timestamp) {
    configuration.setCreatedAt(timestamp);
    configuration.setUpdatedAt(timestamp);
    configuration.setCreatedBy(actor);
    configuration.setUpdatedBy(actor);
  }

  private static void touchAudit(BookingConfiguration configuration, User actor, Date timestamp) {
    configuration.setUpdatedAt(timestamp);
    configuration.setUpdatedBy(actor);
  }

  private BookingConfiguration archive(
      BookingConfiguration configuration, User subject, User actor) {
    return archive(configuration, subject, actor, new Date());
  }

  private BookingConfiguration archive(
      BookingConfiguration configuration, User subject, User actor, Date timestamp) {
    for (TimeSlotBooking booking :
        timeSlotBookings.findFutureConfirmedByConfiguration(configuration.getId(), timestamp)) {
      booking.setState(BookingState.CANCELLED);
      booking.setUpdatedAt(timestamp);
      booking.setUpdatedBy(actor);
      TimeSlotBooking saved = timeSlotBookings.saveAndFlush(booking);
      if (booking.getKind() == BookingEventKind.BOOKING) {
        bookingNotificationService.notify(
            saved, actor, NotificationType.NOTIFICATION_BOOKING_CANCELLED);
      }
      events.publishEvent(
          new TimeSlotBookingAuditEvent(actor, subject, booking, AuditAction.WRITE));
    }
    configuration.setState(BookingConfigurationState.ARCHIVED);
    calendarSubscriptions.deleteByConfigurationId(configuration.getId());
    touchAudit(configuration, actor, timestamp);
    return save(configuration);
  }

  private void notifyAudit(
      User actor, User subject, BookingConfiguration configuration, AuditAction action) {
    events.publishEvent(new BookingConfigurationAuditEvent(actor, subject, configuration, action));
  }

  private static BookableTargetReference validateTarget(ResolvedBookableTarget target) {
    if (target == null
        || target.reference().type() != BookableTargetType.INSTRUMENT
        || !(target.entity() instanceof Instrument instrument)
        || instrument.isTemplate()
        || instrument.isDeleted()
        || !target.reference().id().equals(instrument.getId())) {
      throw new InvalidBookableTargetException();
    }
    return target.reference();
  }

  private static void validateLockedTarget(BookableTargetReference target, Instrument instrument) {
    if (instrument.isTemplate()
        || instrument.isDeleted()
        || !target.id().equals(instrument.getId())) {
      throw new InvalidBookableTargetException();
    }
  }

  private boolean canRead(BookingConfiguration configuration, User subject) {
    return resolveAccess(configuration, subject)
        .hasCapability(ResourceRoleScheme.READ_RESOURCE_CAPABILITY);
  }

  /**
   * Rechecks the target after the configuration lock, so a concurrent Inventory change cannot
   * authorize a configuration mutation from a stale target snapshot.
   */
  private void requireCapability(
      BookingConfiguration configuration, User subject, String capability) {
    ResolvedResourceAccess access = itemPermissions.resolveForMutation(configuration, subject);
    if (!access.hasCapability(capability)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private ResolvedResourceAccess resolveAccess(BookingConfiguration configuration, User subject) {
    return itemPermissions.resolve(configuration, subject);
  }

  private void prepareAccessProjection(List<BookingConfiguration> configurations, User subject) {
    if (configurations.isEmpty()) {
      return;
    }
    java.util.Map<Long, ResolvedResourceAccess> resolvedById =
        itemPermissions.resolveAll(configurations, subject);
    configurations.forEach(
        configuration -> {
          ResolvedResourceAccess resolved =
              resolvedById.getOrDefault(configuration.getId(), ResolvedResourceAccess.none());
          configuration.prepareAccessProjection(
              resolved.effectiveRole().orElse(null),
              resolved.roleSources(),
              capabilities(resolved),
              null);
        });
  }

  private BookingConfigurationCapabilities capabilities(ResolvedResourceAccess resolved) {
    return new BookingConfigurationCapabilities(
        resolved.hasCapability(BookingResourceRoleScheme.EDIT_CONFIGURATION),
        resolved.hasCapability(BookingResourceRoleScheme.VIEW_AUDIT),
        resolved.hasCapability(BookingResourceRoleScheme.READ_RESOURCE),
        false,
        false,
        resolved.hasCapability(BookingResourceRoleScheme.CREATE_BOOKING),
        resolved.hasCapability(BookingResourceRoleScheme.MANAGE_OWN_BOOKINGS),
        resolved.hasCapability(BookingResourceRoleScheme.MANAGE_ALL_EVENTS),
        resolved.hasCapability(BookingResourceRoleScheme.CREATE_BLOCKOUT),
        resolved.hasCapability(BookingResourceRoleScheme.CREATE_CALENDAR_SUBSCRIPTION),
        false);
  }

  private static ResourceRequest idRequest(Long id) {
    return new ResourceRequest(
        new FilterExpression.Comparison("id", Operator.EQUAL, List.of(id), false),
        List.of(),
        new ResourceRequest.Page(1, 1),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private void requireTargetAvailable(BookableTargetReference target, Long configurationId) {
    bookingConfigurationDao
        .findByTarget(target)
        .filter(existing -> !Objects.equals(existing.getId(), configurationId))
        .ifPresent(
            existing -> {
              throw new BookingConfigurationTargetConflictException();
            });
  }

  private BookingConfiguration save(BookingConfiguration configuration) {
    try {
      return bookingConfigurationDao.saveAndFlush(configuration);
    } catch (DataIntegrityViolationException ex) {
      if (isTargetConstraint(ex)) {
        throw new BookingConfigurationTargetConflictException(ex);
      }
      throw ex;
    }
  }

  private static boolean isTargetConstraint(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
          && "UK_BookingConfiguration_target".equals(violation.getConstraintName())) {
        return true;
      }
      if (cause.getMessage() != null
          && cause.getMessage().contains("UK_BookingConfiguration_target")) {
        return true;
      }
    }
    return false;
  }
}
