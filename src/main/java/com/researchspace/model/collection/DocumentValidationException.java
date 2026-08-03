package com.researchspace.model.collection;

import java.util.List;

/** Structured field failures found while parsing a create or update document. */
public final class DocumentValidationException extends RuntimeException {

  private static final long serialVersionUID = 8897495474224791037L;

  public enum Reason {
    INVALID_DOCUMENT("invalidDocument"),
    UNKNOWN_FIELD("unknownField"),
    READ_ONLY("readOnly"),
    NULL_NOT_ALLOWED("nullNotAllowed"),
    WRONG_TYPE("wrongType"),
    INVALID_VALUE("invalidValue"),
    REQUIRED("required");

    private final String code;

    Reason(String code) {
      this.code = code;
    }

    public String code() {
      return code;
    }
  }

  public record Violation(String field, Reason reason) {}

  private final String errorKey;
  private final List<Violation> violations;

  public DocumentValidationException(String errorKey, List<Violation> violations) {
    super(errorKey);
    this.errorKey = errorKey;
    this.violations = List.copyOf(violations);
    if (this.violations.isEmpty()) {
      throw new IllegalArgumentException("Document validation requires at least one violation");
    }
  }

  public String getErrorKey() {
    return errorKey;
  }

  public List<Violation> getViolations() {
    return violations;
  }
}
