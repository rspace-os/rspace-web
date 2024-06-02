package com.researchspace.api.v1.throttling;

import com.researchspace.core.util.throttling.ThrottlingException;

/** Exception thrown when throttling limit has been exceeded. */
public class FileUploadLimitExceededException extends ThrottlingException {

  /** */
  private static final long serialVersionUID = 3649123646426430959L;

  public FileUploadLimitExceededException(String message) {
    super(message);
  }
}
