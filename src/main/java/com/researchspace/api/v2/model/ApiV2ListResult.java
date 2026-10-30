package com.researchspace.api.v2.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.researchspace.model.collection.PageOffsets;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiV2ListResult<T>(
    List<T> docs,
    @Schema(
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            description = "Present only when totals=true.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long totalDocs,
    int limit,
    int page,
    long pagingCounter,
    @Schema(
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            description = "Present only when totals=true.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer totalPages,
    boolean hasPrevPage,
    boolean hasNextPage,
    Integer prevPage,
    Integer nextPage,
    @Schema(description = "True when the requested total exceeds max.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean totalIsLowerBound) {
  /** Caps metadata without changing navigation derived from the page query. */
  public ApiV2ListResult<T> capped(Integer max) {
    if (totalDocs == null) return this;
    boolean capped = max != null && totalDocs > max;
    return new ApiV2ListResult<>(
        docs,
        capped ? max.longValue() : totalDocs,
        limit,
        page,
        pagingCounter,
        capped ? null : totalPages,
        hasPrevPage,
        hasNextPage,
        prevPage,
        nextPage,
        capped);
  }

  /** Builds a page whose navigation can be derived without a count. */
  public static <T> ApiV2ListResult<T> slice(
      List<T> docs, Long total, int limit, int page, boolean hasNext) {
    boolean hasPrev =
        page > 1 && (total == null || page <= (long) Math.ceil((double) total / limit));
    return new ApiV2ListResult<>(
        docs,
        total,
        limit,
        page,
        PageOffsets.offset(page, limit) + 1,
        total == null ? null : (int) Math.ceil((double) total / limit),
        hasPrev,
        hasNext,
        hasPrev ? page - 1 : null,
        hasNext ? page + 1 : null,
        null);
  }

  public static <T> ApiV2ListResult<T> slice(
      List<T> docs, Long total, int limit, int page, boolean hasNext, Integer max) {
    boolean capped = total != null && max != null && total > max;
    var navigation = slice(docs, capped ? null : total, limit, page, hasNext);
    if (total == null) return navigation;
    return new ApiV2ListResult<>(
        docs,
        capped ? max.longValue() : total,
        limit,
        page,
        navigation.pagingCounter(),
        navigation.totalPages(),
        navigation.hasPrevPage(),
        hasNext,
        navigation.prevPage(),
        navigation.nextPage(),
        capped);
  }

  public static <T> ApiV2ListResult<T> counted(
      List<T> docs, long total, int limit, int page, boolean totals) {
    var counted = of(docs, total, limit, page);
    return new ApiV2ListResult<>(
        docs,
        totals ? total : null,
        limit,
        page,
        counted.pagingCounter(),
        totals ? counted.totalPages() : null,
        totals ? counted.hasPrevPage() : page > 1,
        counted.hasNextPage(),
        totals ? counted.prevPage() : page > 1 ? page - 1 : null,
        counted.nextPage(),
        null);
  }

  public static <T> ApiV2ListResult<T> of(List<T> docs, long totalDocs, int limit, int page) {
    int totalPages = (limit > 0 && totalDocs > 0) ? (int) Math.ceil((double) totalDocs / limit) : 0;
    // page > totalPages is out of range: there is no valid page to point prevPage/nextPage at.
    boolean hasPrev = page > 1 && page <= totalPages;
    boolean hasNext = page < totalPages;
    return new ApiV2ListResult<>(
        docs,
        totalDocs,
        limit,
        page,
        PageOffsets.offset(page, limit) + 1,
        totalPages,
        hasPrev,
        hasNext,
        hasPrev ? page - 1 : null,
        hasNext ? page + 1 : null,
        null);
  }
}
