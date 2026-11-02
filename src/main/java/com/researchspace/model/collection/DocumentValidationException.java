package com.researchspace.model.collection;

import java.util.List;
import lombok.Getter;

/** Structured field failures found while parsing a create or update document. */
@Getter
public final class DocumentValidationException extends RuntimeException {

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
}
