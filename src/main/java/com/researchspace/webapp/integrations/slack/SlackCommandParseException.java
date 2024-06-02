package com.researchspace.webapp.integrations.slack;

public class SlackCommandParseException extends Exception {
  /** */
  private static final long serialVersionUID = 1L;

  public SlackCommandParseException(String message) {
    super(message);
  }

  public SlackCommandParseException(String message, Exception cause) {
    super(message, cause);
  }
}
