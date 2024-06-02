package com.researchspace.service.exceptions;

/**
 * Custom exception for dealing with errors when coping {@link
 * com.researchspace.model.record.Record}s
 */
public class RecordCopyException extends RuntimeException {

  public RecordCopyException(String message) {
    super(message);
  }

  public RecordCopyException(String message, Throwable cause) {
    super(message, cause);
  }
}
