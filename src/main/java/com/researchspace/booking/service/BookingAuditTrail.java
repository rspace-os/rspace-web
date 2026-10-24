package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
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
    notifyActorSubjectChange(event.actor(), event.subject(), event.configuration(), event.action());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void bookingConfigurationPermanentlyDeleted(
      BookingConfigurationPermanentDeleteAuditEvent event) {
    String description = "permanent=true; subject=" + event.subject().getUsername();
    auditTrail.notify(
        new GenericEvent(
            event.actor(),
            event.snapshot(),
            com.researchspace.model.audittrail.AuditAction.DELETE,
            description));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void bookingConfigurationDefaultsChanged(BookingConfigurationDefaultsAuditEvent event) {
    notifyActorSubjectChange(event.actor(), event.subject(), event.defaults(), event.action());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void timeSlotBookingChanged(TimeSlotBookingAuditEvent event) {
    notifyActorSubjectChange(event.actor(), event.subject(), event.booking(), event.action());
  }

  private void notifyActorSubjectChange(
      User actor, User subject, Object auditedObject, AuditAction action) {
    if (actor.equals(subject)) {
      auditTrail.notify(new GenericEvent(actor, auditedObject, action));
      return;
    }
    auditTrail.notify(
        new GenericEvent(actor, auditedObject, action, "subject=" + subject.getUsername()));
  }
}
