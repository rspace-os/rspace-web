package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingSchedulingSettings;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("bookingConfigurationDefaultsManager")
@Transactional
public class BookingConfigurationDefaultsManagerImpl
    implements BookingConfigurationDefaultsManager {

  private final BookingConfigurationDefaultsDao defaultsDao;
  private final Validator validator;
  private final ApplicationEventPublisher events;

  public BookingConfigurationDefaultsManagerImpl(
      @Qualifier("bookingConfigurationDefaultsDao") BookingConfigurationDefaultsDao defaultsDao,
      Validator validator,
      ApplicationEventPublisher events) {
    this.defaultsDao = defaultsDao;
    this.validator = validator;
    this.events = events;
  }

  @Override
  public BookingConfigurationDefaults getDefaults(User actor) {
    requireAuthenticated(actor);
    return requiredDefaults();
  }

  @Override
  public BookingConfigurationDefaults updateDefaults(
      BookingSchedulingSettings.Patch schedulingPatch,
      BookingDisplaySettings.Patch displayPatch,
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
    validateSettings(defaults);
    validate(defaults);
    BookingConfigurationDefaults saved = defaultsDao.saveAndFlush(defaults);
    events.publishEvent(
        new BookingConfigurationDefaultsAuditEvent(actor, subject, saved, AuditAction.WRITE));
    return saved;
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
