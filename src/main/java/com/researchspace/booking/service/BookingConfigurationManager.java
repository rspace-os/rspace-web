package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.CollectionMutationLimits;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Shared domain module for reading and changing booking configurations. */
@Service("bookingConfigurationManager")
public class BookingConfigurationManager {

  private final BookingConfigurationDao bookingConfigurationDao;
  private final Validator validator;
  private final ApplicationEventPublisher events;

  public BookingConfigurationManager(
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao bookingConfigurationDao,
      Validator validator,
      ApplicationEventPublisher events) {
    this.bookingConfigurationDao = bookingConfigurationDao;
    this.validator = validator;
    this.events = events;
  }

  /** Values accepted when creating a booking configuration. */
  public record Create(boolean enabled, String timeZone, ResolvedBookableTarget target) {}

  /** Values accepted when changing a booking configuration; {@code null} means unchanged. */
  public record Patch(Boolean enabled, String timeZone, ResolvedBookableTarget target) {}

  /** Returns one page selected by a parsed collection request. */
  public ISearchResults<BookingConfiguration> getConfigurations(ResourceRequest request) {
    return bookingConfigurationDao.getResources(request);
  }

  /** Counts configurations selected by a parsed collection request. */
  public long countConfigurations(ResourceRequest request) {
    return bookingConfigurationDao.countResources(request);
  }

  /** Finds one configuration without throwing when it is absent. */
  public Optional<BookingConfiguration> getConfiguration(Long id) {
    return bookingConfigurationDao.getSafeNull(id);
  }

  /** Authorizes, validates, and persists a new booking configuration. */
  public BookingConfiguration createConfiguration(Create create, User actor) {
    return createConfigurations(List.of(create), actor).get(0);
  }

  /** Authorizes and atomically validates and persists configurations in input order. */
  public List<BookingConfiguration> createConfigurations(List<Create> creates, User actor) {
    authorizeMutation(actor);
    if (creates.size() > CollectionMutationLimits.MAX_BULK_CREATE_ROWS) {
      throw new CollectionMutationException(CollectionMutationException.Reason.BULK_LIMIT);
    }
    List<BookingConfiguration> configurations = creates.stream().map(this::configuration).toList();
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
    saved.forEach(configuration -> notifyAudit(actor, configuration, AuditAction.CREATE));
    return saved;
  }

  private BookingConfiguration configuration(Create create) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setEnabled(create.enabled());
    configuration.setTimeZone(create.timeZone());
    BookableTargetReference target = validateTarget(create.target());
    configuration.replaceTarget(target);
    validate(configuration);
    return configuration;
  }

  /** Authorizes and applies a validated change when the configuration exists. */
  public Optional<BookingConfiguration> updateConfiguration(Long id, Patch patch, User actor) {
    authorizeMutation(actor);
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
              if (!targetChanged && patch.enabled() == null && patch.timeZone() == null) {
                return configuration;
              }
              apply(patch, configuration);
              touchAudit(configuration, actor, new Date());
              validate(configuration);
              BookingConfiguration saved = save(configuration);
              notifyAudit(actor, saved, AuditAction.WRITE);
              return saved;
            });
  }

  /** Authorizes and atomically applies a validated change to the selected configurations. */
  public List<BookingConfiguration> updateConfigurations(
      ResourceRequest request, Patch patch, User actor) {
    List<BookingConfiguration> matches = bulkMatches(request, actor);
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
          validate(configuration);
        });
    matches.forEach(this::save);
    matches.forEach(configuration -> notifyAudit(actor, configuration, AuditAction.WRITE));
    return matches;
  }

  /** Authorizes and removes one configuration, returning its previous value when present. */
  public Optional<BookingConfiguration> removeConfiguration(Long id, User actor) {
    authorizeMutation(actor);
    Optional<BookingConfiguration> configuration = bookingConfigurationDao.getSafeNull(id);
    configuration.ifPresent(ignored -> bookingConfigurationDao.remove(id));
    configuration.ifPresent(deleted -> notifyAudit(actor, deleted, AuditAction.DELETE));
    return configuration;
  }

  /** Authorizes and atomically removes the selected configurations. */
  public List<BookingConfiguration> removeConfigurations(ResourceRequest request, User actor) {
    List<BookingConfiguration> matches = bulkMatches(request, actor);
    matches.forEach(configuration -> bookingConfigurationDao.remove(configuration.getId()));
    matches.forEach(configuration -> notifyAudit(actor, configuration, AuditAction.DELETE));
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
            request, CollectionMutationLimits.MAX_BULK_UPDATE_DELETE_ROWS + 1);
    if (matches.size() > CollectionMutationLimits.MAX_BULK_UPDATE_DELETE_ROWS) {
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

  private void notifyAudit(User actor, BookingConfiguration configuration, AuditAction action) {
    events.publishEvent(new BookingConfigurationAuditEvent(actor, configuration, action));
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
