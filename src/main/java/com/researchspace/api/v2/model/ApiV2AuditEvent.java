package com.researchspace.api.v2.model;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import java.util.Map;

/** One event from the RSpace audit trail. */
public record ApiV2AuditEvent(
    String timestamp,
    String username,
    String fullName,
    AuditDomain domain,
    AuditAction action,
    String description,
    Map<String, Object> payload) {}
