package com.researchspace.api.v2.model;

import java.util.List;

public record ApiV2ListResult<T>(
    List<T> docs,
    long totalDocs,
    int limit,
    int page,
    long pagingCounter,
    int totalPages,
    boolean hasPrevPage,
    boolean hasNextPage,
    Integer prevPage,
    Integer nextPage) {

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
        ((long) page - 1) * limit + 1,
        totalPages,
        hasPrev,
        hasNext,
        hasPrev ? page - 1 : null,
        hasNext ? page + 1 : null);
  }
}
