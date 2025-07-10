package com.researchspace.webapp.integrations.ascenscia.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Base exception class for Ascenscia-related exceptions. */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class AscensciaException extends RuntimeException {

  private HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

  public AscensciaException(String message) {
    super(message);
  }

  public AscensciaException(String message, Throwable cause) {
    super(message, cause);
  }

  public AscensciaException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public AscensciaException(HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
