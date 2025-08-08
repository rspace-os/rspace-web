package com.researchspace.service.chemistry;

/**
 * Unified exception for stoichiometry-specific errors. All stoichiometry domain errors should throw
 * this exception from the manager/service layer and be handled by controllers.
 */
public class StoichiometryException extends RuntimeException {
  public StoichiometryException(String message) {
    super(message);
  }

  public StoichiometryException(String message, Throwable cause) {
    super(message, cause);
  }
}
