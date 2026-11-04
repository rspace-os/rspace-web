package com.researchspace.service.resourceaccess;

import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Writes generic resource-access deltas through the platform's after-commit audit convention. */
@Component
public final class ResourceAccessAuditTrail {

  private final AuditTrailService auditTrail;

  public ResourceAccessAuditTrail(AuditTrailService auditTrail) {
    this.auditTrail = auditTrail;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void accessChanged(ResourceAccessAuditDelta delta) {
    String description =
        "reason="
            + delta.reason()
            + ";grantee="
            + plain(delta.granteeKey())
            + ";name="
            + plain(delta.granteeName())
            + ";oldRole="
            + plain(delta.oldRole())
            + ";newRole="
            + plain(delta.newRole())
            + ";subject="
            + plain(delta.subject().getUsername());
    auditTrail.notify(
        new GenericEvent(delta.actor(), delta.protectedResource(), delta.action(), description));
  }

  private static String plain(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace(";", "\\;").replace("=", "\\=");
  }
}
