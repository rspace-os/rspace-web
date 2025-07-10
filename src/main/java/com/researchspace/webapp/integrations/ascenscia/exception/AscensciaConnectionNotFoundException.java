package com.researchspace.webapp.integrations.ascenscia.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Exception thrown when an Ascenscia connection is not found for a user. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AscensciaConnectionNotFoundException extends AscensciaException {

  public AscensciaConnectionNotFoundException(String message) {
    super(message);
  }

  public AscensciaConnectionNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
