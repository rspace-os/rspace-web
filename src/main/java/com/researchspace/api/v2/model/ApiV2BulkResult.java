package com.researchspace.api.v2.model;

import java.util.List;

/** Payload-shaped result for an atomic bulk operation. */
public record ApiV2BulkResult<T>(List<T> docs, List<ApiV2BulkError> errors) {

  public static <T> ApiV2BulkResult<T> success(List<T> docs) {
    return new ApiV2BulkResult<>(docs, List.of());
  }
}
