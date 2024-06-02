package com.researchspace.api.v1.controller;

/** Exception that indicates that RSpace has disabled access to the API */
public class APIUnavailableException extends RuntimeException {

  /** */
  private static final long serialVersionUID = 1L;

  public APIUnavailableException(String message) {
    super(message);
  }
}
