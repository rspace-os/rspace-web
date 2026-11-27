package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import java.util.Objects;

/** A committed global booking-defaults change waiting for the audit trail. */
public record BookingConfigurationDefaultsAuditEvent(
    User actor, User subject, BookingConfigurationDefaults defaults, AuditAction action) {

  public BookingConfigurationDefaultsAuditEvent {
    Objects.requireNonNull(actor, "Audit actor");
    Objects.requireNonNull(subject, "Audit subject");
    Objects.requireNonNull(defaults, "Audited booking defaults");
    Objects.requireNonNull(action, "Audit action");
  }
}
