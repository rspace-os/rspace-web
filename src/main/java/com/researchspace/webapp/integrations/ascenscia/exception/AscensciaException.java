package com.researchspace.webapp.integrations.ascenscia.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AscensciaException extends RuntimeException {

  private final HttpStatus status;

  public AscensciaException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }
}
