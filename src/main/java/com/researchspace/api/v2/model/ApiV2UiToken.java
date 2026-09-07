package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = "accessToken")
public record ApiV2UiToken(
    @Schema(description = "OAuth bearer token for REST API requests.") String accessToken) {}
