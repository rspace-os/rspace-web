package com.researchspace.service;

public class DocumentAlreadyEditedException extends Exception {

  /** */
  private static final long serialVersionUID = -267702379517341476L;

  public DocumentAlreadyEditedException(String msg) {
    super(msg);
  }
}
