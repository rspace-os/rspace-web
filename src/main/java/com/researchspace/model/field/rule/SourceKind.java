package com.researchspace.model.field.rule;

import com.researchspace.model.field.FieldType;

/**
 * What a field offers to the display rules that read it. Eligibility, the operators a rule may use,
 * validation and evaluation all decide from this one value.
 */
public enum SourceKind {
  /** A radio or choice field; rules read its selected options. */
  OPTIONS,
  /** A one-line text field that is not a password field; rules read its text. */
  TEXT,
  /** A number field; rules read its value as a decimal number. */
  NUMBER,
  /** Any other field, including password fields; it cannot be a source. */
  NONE;

  /**
   * The kind implied by the type alone: {@link #OPTIONS} for radio and choice fields, otherwise
   * {@link #NONE}. Adapters that support text and number sources pass their kind explicitly.
   */
  public static SourceKind ofType(FieldType type) {
    return type == FieldType.RADIO || type == FieldType.CHOICE ? OPTIONS : NONE;
  }
}
