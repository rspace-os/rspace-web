package com.researchspace.archive;

public class ArchivalFileNotExistException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ArchivalFileNotExistException(String message) {
    super(message);
  }

  public ArchivalFileNotExistException(String message, Throwable throwable) {
    super(message, throwable);
  }

  public String getMessage() {
    String msg = super.getMessage() + ": Archive file does not exist.";
    return msg;
  }
}
