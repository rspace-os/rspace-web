package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiV2UiToken(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "OAuth bearer token for REST API requests.")
        String accessToken) {}
