package com.researchspace.service;

/**
 * Thrown by filestore write operations when the requesting user is not allowed to perform the
 * action (e.g. the S3 delete gate: only the creating user may delete an item, and only within the
 * configured window after creation). Mapped to HTTP 403 by {@code ApiControllerAdvice}.
 *
 * <p>Lives in the service layer (not the controller package) so service code can throw it without
 * an upward dependency; the API advice imports it the same way it does {@link
 * DocumentAlreadyEditedException}.
 */
public class FilestoreOperationForbiddenException extends RuntimeException {

  public FilestoreOperationForbiddenException(String message) {
    super(message);
  }
}
