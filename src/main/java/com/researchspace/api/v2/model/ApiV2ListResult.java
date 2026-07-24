package com.researchspace.api.v2.model;

import java.util.List;

public record ApiV2ListResult<T>(
    List<T> docs,
    long totalDocs,
    int limit,
    int page,
    int totalPages,
    boolean hasPrevPage,
    boolean hasNextPage,
    Integer prevPage,
    Integer nextPage) {

  public static <T> ApiV2ListResult<T> of(List<T> docs, long totalDocs, int limit, int page) {
    int totalPages = (limit > 0 && totalDocs > 0) ? (int) Math.ceil((double) totalDocs / limit) : 0;
    boolean hasPrev = totalPages > 0 && page > 1;
    boolean hasNext = page < totalPages;
    return new ApiV2ListResult<>(
        docs,
        totalDocs,
        limit,
        page,
        totalPages,
        hasPrev,
        hasNext,
        hasPrev ? page - 1 : null,
        hasNext ? page + 1 : null);
  }
}
