package com.researchspace.booking.service;

import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Writes booking audit records only after the domain transaction commits successfully. */
@Component
public final class BookingAuditTrail {

  private final AuditTrailService auditTrail;

  public BookingAuditTrail(AuditTrailService auditTrail) {
    this.auditTrail = auditTrail;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void bookingConfigurationChanged(BookingConfigurationAuditEvent event) {
    if (event.actor().equals(event.subject())) {
      auditTrail.notify(new GenericEvent(event.actor(), event.configuration(), event.action()));
      return;
    }
    auditTrail.notify(
        new GenericEvent(
            event.actor(),
            event.configuration(),
            event.action(),
            "subject=" + event.subject().getUsername()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void bookingConfigurationDefaultsChanged(BookingConfigurationDefaultsAuditEvent event) {
    if (event.actor().equals(event.subject())) {
      auditTrail.notify(new GenericEvent(event.actor(), event.defaults(), event.action()));
      return;
    }
    auditTrail.notify(
        new GenericEvent(
            event.actor(),
            event.defaults(),
            event.action(),
            "subject=" + event.subject().getUsername()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void timeSlotBookingChanged(TimeSlotBookingAuditEvent event) {
    if (event.actor().equals(event.subject())) {
      auditTrail.notify(new GenericEvent(event.actor(), event.booking(), event.action()));
      return;
    }
    auditTrail.notify(
        new GenericEvent(
            event.actor(),
            event.booking(),
            event.action(),
            "subject=" + event.subject().getUsername()));
  }
}
