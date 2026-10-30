package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Result for a collection count operation. */
public record ApiV2CountResult(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalDocs,
    @Schema(description = "True when more than max rows match.") boolean totalIsLowerBound) {
  public ApiV2CountResult(long totalDocs) {
    this(totalDocs, false);
  }

  public static ApiV2CountResult of(long total, Integer max) {
    boolean capped = max != null && total > max;
    return new ApiV2CountResult(capped ? max.longValue() : total, capped);
  }
}
