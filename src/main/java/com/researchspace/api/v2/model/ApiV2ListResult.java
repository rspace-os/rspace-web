package com.researchspace.api.v2.model;

import com.researchspace.model.collection.PageOffsets;
import java.util.List;

public record ApiV2ListResult<T>(
    List<T> docs,
    @io.swagger.v3.oas.annotations.media.Schema(
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED,
            description = "Present only when totals=true.")
        @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Long totalDocs,
    int limit,
    int page,
    long pagingCounter,
    @io.swagger.v3.oas.annotations.media.Schema(
            requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED,
            description = "Present only when totals=true.")
        @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Integer totalPages,
    boolean hasPrevPage,
    boolean hasNextPage,
    Integer prevPage,
    Integer nextPage) {

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
        hasNext ? page + 1 : null);
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
        counted.nextPage());
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
        hasNext ? page + 1 : null);
  }
}
