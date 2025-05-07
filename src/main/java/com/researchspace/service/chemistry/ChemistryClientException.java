package com.researchspace.service.chemistry;

public class ChemistryClientException extends RuntimeException {
  public ChemistryClientException(String message) {
    super(message);
  }

  public ChemistryClientException(String message, Exception cause) {
    super(message, cause);
  }
}
