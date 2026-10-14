package com.researchspace.booking.service;

import com.researchspace.model.User;
import java.util.Objects;

/** A committed permanent deletion waiting to be written to the semantic audit trail. */
public record BookingConfigurationPermanentDeleteAuditEvent(
    User actor, User subject, BookingConfigurationPermanentDeleteSnapshot snapshot) {

  public BookingConfigurationPermanentDeleteAuditEvent {
    Objects.requireNonNull(actor, "Audit actor");
    Objects.requireNonNull(subject, "Audit subject");
    Objects.requireNonNull(snapshot, "Permanent-delete snapshot");
  }
}
