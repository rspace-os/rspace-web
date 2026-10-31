package com.researchspace.api.v2.resource;

import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/** A collection failure translated into its public REST API v2 error contract. */
public final class ApiV2ResourceException extends RuntimeException {

  private final HttpStatus status;
  private final String errorCode;
  private final Object[] arguments;
  private final transient Map<String, Object> extensions;

  ApiV2ResourceException(
      RuntimeException cause,
      HttpStatus status,
      String errorCode,
      Object[] arguments,
      Map<String, Object> extensions) {
    super(errorCode, cause);
    this.status = Objects.requireNonNull(status, "Error status");
    this.errorCode = Objects.requireNonNull(errorCode, "Error code");
    this.arguments = Objects.requireNonNull(arguments, "Error arguments").clone();
    this.extensions = Objects.requireNonNull(extensions, "Error extensions");
  }

  public static ApiV2ResourceException of(HttpStatus status, String errorCode) {
    return new ApiV2ResourceException(null, status, errorCode, new Object[0], Map.of());
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

  /** RFC 9457 extension members for the problem body; never {@code null}. */
  public Map<String, Object> extensions() {
    return extensions;
  }
}
