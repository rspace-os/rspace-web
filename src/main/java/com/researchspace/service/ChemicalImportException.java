package com.researchspace.service;

/** Exception thrown when chemical import operations fail. */
public class ChemicalImportException extends Exception {

  public ChemicalImportException(String message) {
    super(message);
  }

  public ChemicalImportException(String message, Throwable cause) {
    super(message, cause);
  }
}
