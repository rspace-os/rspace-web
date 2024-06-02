package com.researchspace.service.archive;

/** General exception class to wrap all lower-level exceptions arising from archive import */
public class ImportFailureException extends RuntimeException {

  /** */
  private static final long serialVersionUID = 8370089589655190875L;

  public ImportFailureException(Exception e) {
    super("Import failed due to exception: " + e.getMessage(), e);
  }

  public ImportFailureException(String message) {
    super("Import failed: " + message);
  }
}
