package com.researchspace.model.collection;

/** A child failure with a path relative to the containing field. */
public final class EmbeddedFieldException extends IllegalArgumentException {
  private final String path;
  private final DocumentValidationException.Reason reason;

  public EmbeddedFieldException(String path, DocumentValidationException.Reason reason) {
    super("Invalid embedded field " + path + ": " + reason.code());
    this.path = path;
    this.reason = reason;
  }

  public String path() {
    return path;
  }

  public DocumentValidationException.Reason reason() {
    return reason;
  }
}
