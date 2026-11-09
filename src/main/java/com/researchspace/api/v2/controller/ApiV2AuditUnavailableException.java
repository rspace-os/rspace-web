package com.researchspace.api.v2.controller;

/** Raised when the audit files cannot be read as one consistent bounded snapshot. */
public class ApiV2AuditUnavailableException extends RuntimeException {

  public ApiV2AuditUnavailableException(Throwable cause) {
    super(cause);
  }
}
