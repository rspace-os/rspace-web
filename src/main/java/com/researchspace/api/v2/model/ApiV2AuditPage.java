package com.researchspace.api.v2.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** One page from a daily bounded-consistency audit snapshot. */
public record ApiV2AuditPage<T>(
    List<T> docs,
    long totalDocs,
    int limit,
    int page,
    long pagingCounter,
    int totalPages,
    boolean hasPrevPage,
    boolean hasNextPage,
    Integer prevPage,
    Integer nextPage,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date") String snapshotDate,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 64, maxLength = 64)
        String snapshotFingerprint) {

  public static <T> ApiV2AuditPage<T> of(
      List<T> docs,
      long totalDocs,
      int limit,
      int page,
      String snapshotDate,
      String snapshotFingerprint) {
    ApiV2ListResult<T> pagination = ApiV2ListResult.of(docs, totalDocs, limit, page);
    return new ApiV2AuditPage<>(
        pagination.docs(),
        pagination.totalDocs(),
        pagination.limit(),
        pagination.page(),
        pagination.pagingCounter(),
        pagination.totalPages(),
        pagination.hasPrevPage(),
        pagination.hasNextPage(),
        pagination.prevPage(),
        pagination.nextPage(),
        snapshotDate,
        snapshotFingerprint);
  }
}
