package com.researchspace.service.chemistry;

public class StoichiometryException extends RuntimeException {
  public StoichiometryException(String message) {
    super(message);
  }

  public StoichiometryException(String message, Throwable cause) {
    super(message, cause);
  }
}
