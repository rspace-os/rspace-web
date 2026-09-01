package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationCapabilities;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDefaultAccessGrantee;
import com.researchspace.model.booking.BookingDefaultSharedWith;
import com.researchspace.model.booking.BookingOwnerHealth;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
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
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessException;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import com.researchspace.service.resourceaccess.ResourceRoleScheme;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Shared domain module for reading and changing booking configurations. */
@Service("bookingConfigurationManager")
public class BookingConfigurationManagerImpl implements BookingConfigurationManager {

  private final BookingConfigurationDao bookingConfigurationDao;
  private final BookingConfigurationDefaultsDao defaultsDao;
  private final InstrumentDao instrumentDao;
  private final Validator validator;
  private final ApplicationEventPublisher events;
  private final ObjectProvider<ResourceRegistry> resourceRegistry;
  private final CollectionDescription<BookingConfiguration> description;
  private final ResourceAccessManager accessManager;
  private final MessageSourceUtils messages;
  private final BookingCalendarSubscriptionDao calendarSubscriptions;

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
      ResourceAccessManager accessManager,
      MessageSourceUtils messages,
      @Qualifier("bookingCalendarSubscriptionDao")
          BookingCalendarSubscriptionDao calendarSubscriptions) {
    this.bookingConfigurationDao = bookingConfigurationDao;
    this.defaultsDao = defaultsDao;
    this.instrumentDao = instrumentDao;
    this.validator = validator;
    this.events = events;
    this.resourceRegistry = resourceRegistry;
    this.description = description;
    this.accessManager = accessManager;
    this.messages = messages;
    this.calendarSubscriptions = calendarSubscriptions;
  }

  /** Returns one page selected by a parsed collection request. */
  @Override
  public ResourcePage<BookingConfiguration> getConfigurations(ResourceRequest request, User actor) {
    ResourcePage<BookingConfiguration> page =
        bookingConfigurationDao.getResources(authorizeRead(request, actor), targetAccess(actor));
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
    configuration.setDeleted(false);
    configuration.setTimeZone(create.timeZone());
    create
        .schedulingSettings()
        .merge(BookingSchedulingSettings.from(defaults))
        .applyTo(configuration);
    BookableTargetReference target = validateTarget(create.target());
    Instrument instrument =
        instrumentDao.lockById(target.id()).orElseThrow(InvalidBookableTargetException::new);
    validateLockedTarget(target, instrument);
    requireCanCreateFor(instrument, subject);
    configuration.replaceTarget(target);
    configuration.setResourceAccess(initialAccess(defaults, subject, actor, timestamp));
    validateSettings(configuration);
    validate(configuration);
    return configuration;
  }

  private ResourceAccess initialAccess(
      BookingConfigurationDefaults defaults, User subject, User actor, Date timestamp) {
    ResourceAccess access =
        new ResourceAccess(BookingResourceRoleScheme.SCHEME_KEY, actor, timestamp);
    access.addAssignment(ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, subject));

    access.addAssignment(
        ResourceRoleAssignment.forAudience(
            defaults.getDefaultSharedWith() == BookingDefaultSharedWith.ALL_USERS
                ? BookingResourceRoleScheme.BOOKER
                : BookingResourceRoleScheme.NO_ACCESS,
            ResourceAudience.ALL_USERS,
            messages.getMessage(ResourceAudience.ALL_USERS.messageKey())));

    if (defaults.getDefaultSharedWith() == BookingDefaultSharedWith.SELECTED) {
      defaults.getSelectedAccessGrantees().stream()
          .map(BookingConfigurationManagerImpl::selectedDefaultAssignment)
          .flatMap(Optional::stream)
          .filter(
              assignment ->
                  !assignment.getGranteeKey().equals(ResourceGranteeKeys.user(subject.getId())))
          .forEach(access::addAssignment);
    }
    long namedAssignments =
        access.getAssignments().stream()
            .filter(assignment -> assignment.getGranteeKind() != ResourceGranteeKind.AUDIENCE)
            .count();
    if (namedAssignments > ResourceAccessManager.MAX_NAMED_ASSIGNMENTS) {
      throw new ResourceAccessException(ResourceAccessException.Reason.ASSIGNMENT_LIMIT);
    }
    return access;
  }

  private static Optional<ResourceRoleAssignment> selectedDefaultAssignment(
      BookingDefaultAccessGrantee grantee) {
    if (grantee.getGranteeKind() == ResourceGranteeKind.USER && grantee.getUser() != null) {
      return Optional.of(
          ResourceRoleAssignment.forUser(BookingResourceRoleScheme.BOOKER, grantee.getUser()));
    }
    if (grantee.getGranteeKind() == ResourceGranteeKind.GROUP && grantee.getGroup() != null) {
      return Optional.of(
          ResourceRoleAssignment.forGroup(BookingResourceRoleScheme.BOOKER, grantee.getGroup()));
    }
    return Optional.empty();
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
            .filter(configuration -> !configuration.isDeleted() && canRead(configuration, subject))
            .map(
                configuration -> {
                  if (expectedVersion != null
                      && configuration.getConfigurationVersion() != expectedVersion) {
                    throw new BookingConcurrentModificationException();
                  }
                  requireCapability(
                      configuration, subject, BookingResourceRoleScheme.EDIT_CONFIGURATION);
                  if (unchanged(patch)) {
                    return configuration;
                  }
                  apply(patch, configuration);
                  touchAudit(configuration, actor, new Date());
                  validateSettings(configuration);
                  validate(configuration);
                  BookingConfiguration saved = save(configuration);
                  notifyAudit(actor, subject, saved, AuditAction.WRITE);
                  return saved;
                });
    updated.ifPresent(value -> prepareAccessProjection(List.of(value), subject));
    return updated;
  }

  @Override
  public List<BookingConfiguration> updateConfigurations(
      ResourceRequest request, Patch patch, User subject, User actor) {
    List<BookingConfiguration> matches = bulkMatches(request, subject);
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
  public Optional<BookingConfiguration> removeConfiguration(Long id, User subject, User actor) {
    requireAuthenticated(subject);
    Optional<BookingConfiguration> configuration =
        bookingConfigurationDao
            .lockById(id)
            .filter(value -> !value.isDeleted() && canRead(value, subject));
    configuration.ifPresent(
        value ->
            requireCapability(value, subject, BookingResourceRoleScheme.ARCHIVE_CONFIGURATION));
    configuration = configuration.map(existing -> archive(existing, actor));
    configuration.ifPresent(deleted -> notifyAudit(actor, subject, deleted, AuditAction.DELETE));
    return configuration;
  }

  @Override
  public List<BookingConfiguration> removeConfigurations(
      ResourceRequest request, User subject, User actor) {
    List<BookingConfiguration> matches = bulkMatches(request, subject);
    Date now = new Date();
    matches.forEach(configuration -> archive(configuration, actor, now));
    matches.forEach(
        configuration -> notifyAudit(actor, subject, configuration, AuditAction.DELETE));
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
  }

  private static boolean unchanged(Patch patch) {
    return patch.enabled() == null
        && patch.timeZone() == null
        && patch.schedulingSettings().isEmpty();
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

  private BookingConfiguration archive(BookingConfiguration configuration, User actor) {
    return archive(configuration, actor, new Date());
  }

  private BookingConfiguration archive(
      BookingConfiguration configuration, User actor, Date timestamp) {
    configuration.setDeleted(true);
    configuration.setEnabled(false);
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

  private static void requireCanCreateFor(Instrument instrument, User subject) {
    if (subject.hasSysadminRole()) {
      return;
    }
    if (instrument.getOwner() == null
        || instrument.getOwner().getId() == null
        || !instrument.getOwner().getId().equals(subject.getId())) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private boolean canRead(BookingConfiguration configuration, User subject) {
    return accessManager
        .resolve(configuration.getResourceAccess(), subject)
        .hasCapability(ResourceRoleScheme.READ_RESOURCE_CAPABILITY);
  }

  private void requireCapability(
      BookingConfiguration configuration, User subject, String capability) {
    ResolvedResourceAccess access =
        accessManager.resolve(configuration.getResourceAccess(), subject);
    if (!access.hasCapability(capability)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private void prepareAccessProjection(List<BookingConfiguration> configurations, User subject) {
    if (configurations.isEmpty()) {
      return;
    }
    List<ResourceAccess> persisted =
        configurations.stream()
            .map(BookingConfiguration::getResourceAccess)
            .filter(access -> access.getId() != null)
            .toList();
    java.util.Map<Long, ResolvedResourceAccess> resolvedById =
        persisted.isEmpty() ? java.util.Map.of() : accessManager.resolveAll(persisted, subject);
    configurations.forEach(
        configuration -> {
          ResourceAccess aggregate = configuration.getResourceAccess();
          ResolvedResourceAccess resolved =
              aggregate.getId() == null
                  ? accessManager.resolve(aggregate, subject)
                  : resolvedById.getOrDefault(aggregate.getId(), ResolvedResourceAccess.none());
          configuration.prepareAccessProjection(
              resolved.effectiveRole().orElse(null),
              resolved.roleSources(),
              capabilities(aggregate, subject, resolved),
              new BookingOwnerHealth(hasEffectiveOwner(aggregate)));
        });
  }

  private BookingConfigurationCapabilities capabilities(
      ResourceAccess aggregate, User subject, ResolvedResourceAccess resolved) {
    return new BookingConfigurationCapabilities(
        resolved.hasCapability(BookingResourceRoleScheme.EDIT_CONFIGURATION),
        resolved.hasCapability(BookingResourceRoleScheme.ARCHIVE_CONFIGURATION),
        resolved.hasCapability(BookingResourceRoleScheme.VIEW_AUDIT),
        resolved.hasCapability(BookingResourceRoleScheme.MANAGE_ASSIGNMENTS),
        resolved.hasCapability(BookingResourceRoleScheme.MANAGE_ASSIGNMENTS),
        resolved.hasCapability(BookingResourceRoleScheme.MANAGE_OWNERS),
        resolved.hasCapability(BookingResourceRoleScheme.CREATE_BOOKING),
        resolved.hasCapability(BookingResourceRoleScheme.MANAGE_OWN_BOOKINGS),
        resolved.hasCapability(BookingResourceRoleScheme.MANAGE_ALL_EVENTS),
        resolved.hasCapability(BookingResourceRoleScheme.CREATE_BLOCKOUT),
        resolved.hasCapability(BookingResourceRoleScheme.CREATE_CALENDAR_SUBSCRIPTION),
        accessManager.canLeave(aggregate, subject));
  }

  private static boolean hasEffectiveOwner(ResourceAccess aggregate) {
    return aggregate.getAssignments().stream()
        .filter(assignment -> assignment.getRoleKey().equals(BookingResourceRoleScheme.OWNER))
        .anyMatch(
            assignment ->
                switch (assignment.getGranteeKind()) {
                  case USER -> assignment.getUser() != null && assignment.getUser().isEnabled();
                  case GROUP ->
                      assignment.getGroup() != null
                          && assignment.getGroup().getEnabledMemberSize() > 0;
                  case AUDIENCE -> false;
                });
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
