package com.researchspace.api.v2.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Objects;
import org.springframework.http.MediaType;

/** RFC 9457 problem details using the default {@code about:blank} type. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiV2Problem(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
    @Schema(nullable = true) String detail,
    @Schema(nullable = true) List<InvalidParam> invalidParams) {

  public ApiV2Problem {
    Objects.requireNonNull(title, "Problem title");
    Objects.requireNonNull(code, "Problem code");
  }

  public record InvalidParam(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason) {}

  public static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");

}
