package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;

/** RFC 9457 problem details with optional, feature-supplied extension members. */
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiV2Problem(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Stable summary for this problem code.")
        String title,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(nullable = true, description = "Optional explanation specific to this occurrence.")
        String detail,
    @Schema(nullable = true) List<InvalidParam> invalidParams,
    @JsonAnyGetter @Schema(hidden = true) Map<String, Object> extensions) {
  public ApiV2Problem {
    Objects.requireNonNull(title, "Problem title");
    Objects.requireNonNull(code, "Problem code");
    extensions = Map.copyOf(extensions);
  }

  public record InvalidParam(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason) {}

  public static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");
}
