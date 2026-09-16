package com.researchspace.dao.query;

import java.util.List;

/** Applies the limit-plus-one convention used by runtime-field catalog pages. */
public final class LookAheadPagination {

  private LookAheadPagination() {}

  public record Result<T>(List<T> rows, Long total, boolean hasMore) {}

  public static <T> Result<T> apply(List<T> rows, int offset, int limit, boolean hydrating) {
    boolean hasMore = !hydrating && rows.size() > limit;
    if (hasMore) {
      rows.remove(rows.size() - 1);
    }
    boolean exact = hydrating || !rows.isEmpty() || offset == 0;
    return new Result<>(rows, hasMore || !exact ? null : (long) offset + rows.size(), hasMore);
  }
}
