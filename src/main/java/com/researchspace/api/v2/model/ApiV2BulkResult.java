package com.researchspace.api.v2.model;

import java.util.List;

/**
 * Result of an atomic bulk operation.
 *
 * <p>No {@code errors} array. A bulk request is all-or-nothing here: one invalid document fails the
 * whole batch and the client gets an RFC 9457 problem naming the array position, so a per-document
 * error list could only ever be empty. Publishing it in OpenAPI meant a generated SDK carried
 * partial-failure handling that no response could trigger.
 */
public record ApiV2BulkResult<T>(List<T> docs) {

  public static <T> ApiV2BulkResult<T> success(List<T> docs) {
    return new ApiV2BulkResult<>(docs);
  }
}
