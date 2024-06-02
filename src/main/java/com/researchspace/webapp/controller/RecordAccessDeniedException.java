package com.researchspace.webapp.controller;

/** Catch-all exception for either a record no longer existing, never existed, or no access. */
public class RecordAccessDeniedException extends Exception {

  public RecordAccessDeniedException(String message) {
    super(message);
  }

  /** */
  private static final long serialVersionUID = -4499476009793973091L;
}
