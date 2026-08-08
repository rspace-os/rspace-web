package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiV2BulkError(
    @Schema(nullable = true) String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(nullable = true) String detail) {}
