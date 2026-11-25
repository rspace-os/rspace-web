package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Newest authorized events within the effective completed-day window. */
public record ApiV2AuditResult<T>(
    List<T> docs,
    int limit,
    boolean truncated,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") String start,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time")
        String endExclusive) {}
