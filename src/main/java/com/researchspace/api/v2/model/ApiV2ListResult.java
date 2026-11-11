package com.researchspace.api.v2.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ApiV2ListResult<T>(
    List<T> docs,
    int page,
    int limit,
    boolean hasNextPage,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long totalDocs,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean totalIsLowerBound) {
  /** Derives navigation from the known total, independently of optional capped metadata. */
  public static <T> ApiV2ListResult<T> counted(
      List<T> docs, long total, int page, int limit, boolean includeTotals, Integer max) {
    boolean hasNext = limit > 0 && page < (long) Math.ceil((double) total / limit);
    return slice(docs, page, limit, hasNext, includeTotals ? total : null, max);
  }

  /** Preserves slice lookahead navigation without requiring a count. */
  public static <T> ApiV2ListResult<T> slice(
      List<T> docs, int page, int limit, boolean hasNext, Long total, Integer max) {
    boolean capped = total != null && max != null && total > max;
    return new ApiV2ListResult<>(
        docs,
        page,
        limit,
        hasNext,
        capped ? Long.valueOf(max) : total,
        total == null ? null : capped);
  }
}
