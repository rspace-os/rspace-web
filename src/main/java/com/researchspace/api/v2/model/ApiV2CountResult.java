package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Result for a collection count operation. */
public record ApiV2CountResult(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalDocs) {}
