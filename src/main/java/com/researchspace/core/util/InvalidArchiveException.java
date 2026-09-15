package com.researchspace.core.util;

import java.io.IOException;

/**
 * Thrown when an uploaded archive is rejected as unsafe or malformed. By the time this propagates,
 * anything the failed extraction wrote has been removed.
 */
public class InvalidArchiveException extends IOException {

  public InvalidArchiveException(String message) {
    super("Invalid or unsafe archive: " + message);
  }
}
