package com.researchspace.webapp.integrations.ascenscia.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception thrown when authentication with Ascenscia fails. */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AscensciaAuthenticationException extends AscensciaException {

  public AscensciaAuthenticationException(String message) {
    super(message);
  }

  public AscensciaAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
