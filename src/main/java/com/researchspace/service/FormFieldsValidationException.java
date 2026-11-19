package com.researchspace.service;

import java.util.List;

/**
 * Rejects a whole form field save. Unchecked, so the manager's transaction rolls back every change
 * the save had already applied.
 */
public class FormFieldsValidationException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final List<String> errors;

  /**
   * @param errors resolved, user-facing messages; at least one
   */
  public FormFieldsValidationException(List<String> errors) {
    super(String.join("; ", errors));
    this.errors = List.copyOf(errors);
  }

  /** The resolved messages, one per problem. */
  public List<String> getErrors() {
    return errors;
  }
}
