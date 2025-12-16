package com.researchspace.service;

public class FolderNotSharedException extends IllegalArgumentException {

  public FolderNotSharedException(String message) {
    super(message);
  }

  public FolderNotSharedException(String message, Throwable cause) {
    super(message, cause);
  }
}
