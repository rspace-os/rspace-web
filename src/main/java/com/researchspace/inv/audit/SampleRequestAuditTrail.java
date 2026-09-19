package com.researchspace.inv.audit;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.events.SampleRequestStatusEvent;
import com.researchspace.model.inventory.SampleRequestStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Logs sample request lifecycle events to the audit trail. */
@Component
public class SampleRequestAuditTrail {

  private @Autowired AuditTrailService auditer;

  @TransactionalEventListener
  public void sampleRequestStatusRecorded(SampleRequestStatusEvent event) {
    auditer.notify(
        new GenericEvent(
            event.getActor(), event.getRequest(), actionFor(event.getRequest().getStatus())));
  }

  /** Exhaustive, so a new status cannot be added without deciding how it is audited. */
  private AuditAction actionFor(SampleRequestStatus status) {
    return switch (status) {
      case PENDING -> AuditAction.REQUEST_SENT;
      case APPROVED -> AuditAction.REQUEST_APPROVED;
      case REJECTED -> AuditAction.REQUEST_REJECTED;
      case CANCELLED -> AuditAction.REQUEST_CANCELLED;
      case FULFILLED -> AuditAction.REQUEST_FULFILLED;
    };
  }
}
