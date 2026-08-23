package com.researchspace.api.v2.model;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** One event from the RSpace audit trail. */
public record ApiV2AuditEvent(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") String timestamp,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String username,
    @Schema(nullable = true) String fullName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AuditDomain domain,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AuditAction action,
    @Schema(nullable = true) String description,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "object")
        Map<String, Object> payload) {}
