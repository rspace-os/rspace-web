package com.researchspace.api.v2.resource;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/** A collection failure translated into its public REST API v2 error contract. */
public final class ApiV2ResourceException extends RuntimeException {

  private final HttpStatus status;
  private final String errorCode;
  private final Object[] arguments;

  ApiV2ResourceException(
      RuntimeException cause, HttpStatus status, String errorCode, Object[] arguments) {
    super(errorCode, cause);
    this.status = Objects.requireNonNull(status, "Error status");
    this.errorCode = Objects.requireNonNull(errorCode, "Error code");
    this.arguments = Objects.requireNonNull(arguments, "Error arguments").clone();
  }

  public static ApiV2ResourceException of(HttpStatus status, String errorCode) {
    return new ApiV2ResourceException(null, status, errorCode, new Object[0]);
  }

  public HttpStatus status() {
    return status;
  }

  public String errorCode() {
    return errorCode;
  }

  public Object[] arguments() {
    return arguments.clone();
  }
}
