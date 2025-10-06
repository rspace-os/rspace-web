package com.researchspace.service.chemistry;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ChemistryClientException extends RuntimeException {
  private final HttpStatus status;

  public ChemistryClientException(String message) {
    super(message);
    this.status = null;
  }

  public ChemistryClientException(String message, Exception cause) {
    super(message, cause);
    this.status = null;
  }

  public ChemistryClientException(String message, HttpStatus status, Throwable cause) {
    super(message, cause);
    this.status = status;
  }
}
