package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.CollectionMutationException;
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
  private final Validator validator;
  private final ApplicationEventPublisher events;
  private final ObjectProvider<ResourceRegistry> resourceRegistry;

  public BookingConfigurationManagerImpl(
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao bookingConfigurationDao,
      @Qualifier("bookingConfigurationDefaultsDao") BookingConfigurationDefaultsDao defaultsDao,
      Validator validator,
      ApplicationEventPublisher events,
      ObjectProvider<ResourceRegistry> resourceRegistry) {
    this.bookingConfigurationDao = bookingConfigurationDao;
    this.defaultsDao = defaultsDao;
    this.validator = validator;
    this.events = events;
    this.resourceRegistry = resourceRegistry;
  }

  /** Returns one page selected by a parsed collection request. */
  @Override
  public ResourcePage<BookingConfiguration> getConfigurations(ResourceRequest request, User actor) {
    authorizeRead(actor);
    return bookingConfigurationDao.getResources(request, targetAccess(actor));
  }

  /** Counts configurations selected by a parsed collection request. */
  @Override
  public long countConfigurations(ResourceRequest request, User actor) {
    authorizeRead(actor);
    return bookingConfigurationDao.countResources(request, targetAccess(actor));
  }

  /** Finds one configuration without throwing when it is absent. */
  @Override
  public Optional<BookingConfiguration> getConfiguration(Long id, User actor) {
    authorizeRead(actor);
    return bookingConfigurationDao.getSafeNull(id);
  }

  private void authorizeRead(User actor) {
    if (actor == null) {
      throw new AuthorizationException("errors.api.v2.authenticationRequired");
    }
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
    authorizeMutation(subject);
    if (creates.size() > ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkCreateRows()) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }
    BookingConfigurationDefaults defaults =
        defaultsDao
            .getSafeNull(BookingConfigurationDefaults.SINGLETON_ID)
            .orElseThrow(
                () -> new IllegalStateException("Booking configuration defaults row is missing"));
    List<BookingConfiguration> configurations =
        creates.stream().map(create -> configuration(create, defaults)).toList();
    Date now = new Date();
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
    return saved;
  }

  private BookingConfiguration configuration(Create create, BookingConfigurationDefaults defaults) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setEnabled(create.enabled());
    configuration.setTimeZone(create.timeZone());
    create
        .schedulingSettings()
        .merge(BookingSchedulingSettings.from(defaults))
        .applyTo(configuration);
    BookableTargetReference target = validateTarget(create.target());
    configuration.replaceTarget(target);
    validateSettings(configuration);
    validate(configuration);
    return configuration;
  }

  @Override
  public Optional<BookingConfiguration> updateConfiguration(
      Long id, Patch patch, User subject, User actor) {
    authorizeMutation(subject);
    return bookingConfigurationDao
        .getSafeNull(id)
        .map(
            configuration -> {
              boolean targetChanged = false;
              if (patch.target() != null) {
                BookableTargetReference target = validateTarget(patch.target());
                if (!target.equals(configuration.getTarget())) {
                  requireTargetAvailable(target, configuration.getId());
                  configuration.replaceTarget(target);
                  targetChanged = true;
                }
              }
              if (!targetChanged && unchanged(patch)) {
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
  }

  @Override
  public List<BookingConfiguration> updateConfigurations(
      ResourceRequest request, Patch patch, User subject, User actor) {
    List<BookingConfiguration> matches = bulkMatches(request, subject);
    if (patch.target() != null) {
      BookableTargetReference target = validateTarget(patch.target());
      if (matches.size() > 1) {
        throw new BookingConfigurationTargetConflictException();
      }
      if (!matches.isEmpty() && !target.equals(matches.get(0).getTarget())) {
        requireTargetAvailable(target, matches.get(0).getId());
        matches.get(0).replaceTarget(target);
      }
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
    return matches;
  }

  @Override
  public Optional<BookingConfiguration> removeConfiguration(Long id, User subject, User actor) {
    authorizeMutation(subject);
    Optional<BookingConfiguration> configuration = bookingConfigurationDao.getSafeNull(id);
    configuration.ifPresent(ignored -> bookingConfigurationDao.remove(id));
    configuration.ifPresent(deleted -> notifyAudit(actor, subject, deleted, AuditAction.DELETE));
    return configuration;
  }

  @Override
  public List<BookingConfiguration> removeConfigurations(
      ResourceRequest request, User subject, User actor) {
    List<BookingConfiguration> matches = bulkMatches(request, subject);
    matches.forEach(configuration -> bookingConfigurationDao.remove(configuration.getId()));
    matches.forEach(
        configuration -> notifyAudit(actor, subject, configuration, AuditAction.DELETE));
    return matches;
  }

  private void authorizeMutation(User actor) {
    if (actor == null || !actor.hasRole(Role.SYSTEM_ROLE)) {
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
    authorizeMutation(actor);
    if (request.filter() == null) {
      throw new CollectionMutationException(CollectionMutationException.Reason.FILTER_REQUIRED);
    }
    List<BookingConfiguration> matches =
        bookingConfigurationDao.getResources(
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
