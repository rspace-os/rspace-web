package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.TimeSlotBooking;
import java.util.Objects;
import org.hibernate.Hibernate;

/** A committed time-slot booking change waiting for the searchable audit trail. */
public record TimeSlotBookingAuditEvent(
    User actor, User subject, TimeSlotBooking booking, AuditAction action) {

  public TimeSlotBookingAuditEvent {
    Objects.requireNonNull(actor, "Audit actor");
    Objects.requireNonNull(subject, "Audit subject");
    Objects.requireNonNull(booking, "Audited booking");
    Objects.requireNonNull(action, "Audit action");
    // Proxy getters lack the audit annotations. Resolve the entity while its session is open.
    booking = Hibernate.unproxy(booking, TimeSlotBooking.class);
  }
}
