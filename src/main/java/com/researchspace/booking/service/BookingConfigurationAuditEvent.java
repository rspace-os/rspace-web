package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookingConfiguration;
import java.util.Objects;

/** A committed booking-configuration change waiting to be written to the audit trail. */
public record BookingConfigurationAuditEvent(
    User actor, BookingConfiguration configuration, AuditAction action) {

  public BookingConfigurationAuditEvent {
    Objects.requireNonNull(actor, "Audit actor");
    Objects.requireNonNull(configuration, "Audited booking configuration");
    Objects.requireNonNull(action, "Audit action");
  }
}
