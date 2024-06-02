package com.researchspace.webapp.integrations.slack;

public class SlackErrorResponseException extends Exception {
  /** */
  private static final long serialVersionUID = 1L;

  public SlackErrorResponseException(String message) {
    super(message);
  }
}
