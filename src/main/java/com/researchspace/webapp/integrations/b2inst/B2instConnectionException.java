package com.researchspace.webapp.integrations.b2inst;

/**
 * Thrown when a call to the B2INST API fails at the transport level. Mirrors the role of {@code
 * com.researchspace.datacite.model.DataCiteConnectionException} for the DataCite connector.
 */
public class B2instConnectionException extends RuntimeException {

  public B2instConnectionException(String message) {
    super(message);
  }

  public B2instConnectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
