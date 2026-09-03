package com.researchspace.core.util;

/**
 * Thrown when an upload's filename lacks an accepted extension; a dedicated type lets callers
 * handle it without catching unrelated {@link IllegalArgumentException}s.
 */
public class UnsupportedFileExtensionException extends IllegalArgumentException {

  public UnsupportedFileExtensionException(String message) {
    super(message);
  }
}
