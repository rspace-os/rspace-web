package com.researchspace.webapp.integrations.dsw.exception;

import org.springframework.web.client.RestClientException;

/**
 * The DSW server's version could not be checked, so its API paths are unknown. Kept separate from
 * the errors of the authenticated API calls, which can mean a wrong API key: the version check
 * sends no credentials.
 */
public class DSWVersionCheckException extends RestClientException {

  public enum Reason {
    /** The saved URL redirects somewhere that is not followed, such as from http to https. */
    REDIRECT,
    /** Any other failure, such as a timeout, connection error, 429 or 5xx status. */
    UNAVAILABLE
  }

  private final Reason reason;

  public DSWVersionCheckException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public DSWVersionCheckException(Reason reason, String message, Throwable cause) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }
}
