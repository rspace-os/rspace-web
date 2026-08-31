package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.dao.resourceaccess.ResourceAccessDao;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDefaultAccessGrantee;
import com.researchspace.model.booking.BookingDefaultSharedWith;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("bookingConfigurationDefaultsManager")
@Transactional
public class BookingConfigurationDefaultsManagerImpl
    implements BookingConfigurationDefaultsManager {

  private final BookingConfigurationDefaultsDao defaultsDao;
  private final ResourceAccessDao resourceAccessDao;
  private final Validator validator;
  private final ApplicationEventPublisher events;

  public BookingConfigurationDefaultsManagerImpl(
      @Qualifier("bookingConfigurationDefaultsDao") BookingConfigurationDefaultsDao defaultsDao,
      ResourceAccessDao resourceAccessDao,
      Validator validator,
      ApplicationEventPublisher events) {
    this.defaultsDao = defaultsDao;
    this.resourceAccessDao = resourceAccessDao;
    this.validator = validator;
    this.events = events;
  }

  @Override
  public BookingConfigurationDefaults getDefaults(User actor) {
    requireAuthenticated(actor);
    return initializeSelectedGrantees(requiredDefaults());
  }

  @Override
  public BookingConfigurationDefaults updateDefaults(
      BookingSchedulingSettings.Patch schedulingPatch,
      BookingDisplaySettings.Patch displayPatch,
      long expectedVersion,
      User subject,
      User actor) {
    return updateDefaults(
        schedulingPatch, displayPatch, null, null, expectedVersion, subject, actor);
  }

  @Override
  public BookingConfigurationDefaults updateDefaults(
      BookingSchedulingSettings.Patch schedulingPatch,
      BookingDisplaySettings.Patch displayPatch,
      BookingDefaultSharedWith defaultSharedWith,
      List<String> selectedGranteeKeys,
      long expectedVersion,
      User subject,
      User actor) {
    requireSysadmin(subject);
    BookingConfigurationDefaults defaults = lockedDefaults();
    if (defaults.getConfigurationVersion() != expectedVersion) {
      throw new StaleBookingSettingsException();
    }
    schedulingPatch.merge(BookingSchedulingSettings.from(defaults)).applyTo(defaults);
    BookingDisplaySettings displaySettings =
        displayPatch.merge(BookingDisplaySettings.from(defaults));
    requireValid(displaySettings);
    displaySettings.applyTo(defaults);
    applyDefaultSharing(defaults, defaultSharedWith, selectedGranteeKeys);
    validateSettings(defaults);
    validate(defaults);
    BookingConfigurationDefaults saved = defaultsDao.saveAndFlush(defaults);
    initializeSelectedGrantees(saved);
    events.publishEvent(
        new BookingConfigurationDefaultsAuditEvent(actor, subject, saved, AuditAction.WRITE));
    return saved;
  }

  private static BookingConfigurationDefaults initializeSelectedGrantees(
      BookingConfigurationDefaults defaults) {
    Hibernate.initialize(defaults.getSelectedAccessGrantees());
    defaults
        .getSelectedAccessGrantees()
        .forEach(
            grantee -> {
              Hibernate.initialize(grantee.getUser());
              Hibernate.initialize(grantee.getGroup());
            });
    return defaults;
  }

  private void applyDefaultSharing(
      BookingConfigurationDefaults defaults,
      BookingDefaultSharedWith requestedMode,
      List<String> requestedKeys) {
    BookingDefaultSharedWith mode =
        requestedMode == null ? defaults.getDefaultSharedWith() : requestedMode;
    if (mode != BookingDefaultSharedWith.SELECTED) {
      if (requestedKeys != null && !requestedKeys.isEmpty()) {
        throw new InvalidBookingDefaultSharingException();
      }
      defaults.setDefaultSharedWith(mode);
      defaults.replaceSelectedAccessGrantees(List.of());
      return;
    }

    List<String> keys =
        requestedKeys == null
            ? defaults.getSelectedAccessGrantees().stream()
                .map(BookingDefaultAccessGrantee::getGranteeKey)
                .toList()
            : requestedKeys;
    if (keys.isEmpty()) {
      throw new InvalidBookingDefaultSharingException();
    }
    Map<String, BookingDefaultAccessGrantee> existing = new LinkedHashMap<>();
    defaults
        .getSelectedAccessGrantees()
        .forEach(grantee -> existing.put(grantee.getGranteeKey(), grantee));
    Map<String, Boolean> unique = new LinkedHashMap<>();
    List<BookingDefaultAccessGrantee> replacement = new ArrayList<>();
    for (String key : keys) {
      if (key == null || unique.put(key, Boolean.TRUE) != null) {
        throw new InvalidBookingDefaultSharingException();
      }
      BookingDefaultAccessGrantee retained = existing.get(key);
      if (retained != null) {
        replacement.add(retained);
        continue;
      }
      ResourceRoleAssignment resolved =
          resourceAccessDao.resolveAvailable(key, BookingResourceRoleScheme.BOOKER);
      if (resolved == null || resolved.getGranteeKind() == ResourceGranteeKind.AUDIENCE) {
        throw new InvalidBookingDefaultSharingException();
      }
      replacement.add(
          resolved.getGranteeKind() == ResourceGranteeKind.USER
              ? BookingDefaultAccessGrantee.forUser(resolved.getUser())
              : BookingDefaultAccessGrantee.forGroup(resolved.getGroup()));
    }
    defaults.setDefaultSharedWith(mode);
    defaults.replaceSelectedAccessGrantees(replacement);
  }

  private BookingConfigurationDefaults requiredDefaults() {
    return defaultsDao
        .getSafeNull(BookingConfigurationDefaults.SINGLETON_ID)
        .orElseThrow(
            () -> new IllegalStateException("Booking configuration defaults row is missing"));
  }

  private BookingConfigurationDefaults lockedDefaults() {
    return defaultsDao
        .lockSingleton()
        .orElseThrow(
            () -> new IllegalStateException("Booking configuration defaults row is missing"));
  }

  private void validate(BookingConfigurationDefaults defaults) {
    Set<ConstraintViolation<BookingConfigurationDefaults>> violations =
        validator.validate(defaults);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  static void validateSettings(BookingConfigurationDefaults defaults) {
    BookingSettingsValidation.requireValid(BookingSchedulingSettings.from(defaults));
    requireValid(BookingDisplaySettings.from(defaults));
  }

  static void requireValid(BookingDisplaySettings settings) {
    if (!settings.hasValidAvailabilityWindow()) {
      throw new InvalidBookingDisplaySettingsException(
          InvalidBookingDisplaySettingsException.Reason.AVAILABILITY_WINDOW);
    }
    if (!settings.hasValidTimezoneChoice()) {
      throw new InvalidBookingDisplaySettingsException(
          InvalidBookingDisplaySettingsException.Reason.TIMEZONE);
    }
  }

  private static void requireAuthenticated(User actor) {
    if (actor == null) {
      throw new AuthorizationException("errors.api.v2.authenticationRequired");
    }
  }

  private static void requireSysadmin(User actor) {
    if (actor == null || !actor.hasRole(Role.SYSTEM_ROLE)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }
}
