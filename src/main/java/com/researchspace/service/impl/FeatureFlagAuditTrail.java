package com.researchspace.service.impl;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Writes feature flag audit records after the domain transaction commits. */
@Component
public final class FeatureFlagAuditTrail {

  private final AuditTrailService auditTrail;

  public FeatureFlagAuditTrail(AuditTrailService auditTrail) {
    this.auditTrail = auditTrail;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void featureFlagChanged(FeatureFlagResourceChangedEvent event) {
    String description =
        event.actor().equals(event.subject()) ? null : "subject=" + event.subject().getUsername();
    auditTrail.notify(
        new GenericEvent(event.actor(), event.resource(), AuditAction.WRITE, description));
  }
}
