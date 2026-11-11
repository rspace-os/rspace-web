package com.researchspace.service.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import java.time.Instant;
import java.util.Objects;

/** One committed-intent access change, delivered to the audit sink after transaction commit. */
public record ResourceAccessAuditDelta(
    Object protectedResource,
    User actor,
    User subject,
    AuditAction action,
    String granteeKey,
    String granteeName,
    String oldRole,
    String newRole,
    Instant timestamp,
    ResourceAccessAuditReason reason) {

  public ResourceAccessAuditDelta {
    Objects.requireNonNull(protectedResource, "protectedResource");
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(granteeKey, "granteeKey");
    Objects.requireNonNull(granteeName, "granteeName");
    Objects.requireNonNull(timestamp, "timestamp");
    Objects.requireNonNull(reason, "reason");
  }
}
