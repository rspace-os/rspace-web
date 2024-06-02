package com.researchspace.service.archive.export;

public class ExportFailureException extends RuntimeException {

  public ExportFailureException(String message) {
    super(message);
  }

  public ExportFailureException(String message, Exception cause) {
    super(message, cause);
  }

  public ExportFailureException(Exception ex) {
    super(ex);
  }

  /** */
  private static final long serialVersionUID = 1L;
}
