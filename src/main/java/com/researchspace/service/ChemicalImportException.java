package com.researchspace.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ChemicalImportException extends Exception {

  private final HttpStatus status;

  public ChemicalImportException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }

  public ChemicalImportException(String message, Throwable cause, HttpStatus status) {
    super(message, cause);
    this.status = status;
  }
}
