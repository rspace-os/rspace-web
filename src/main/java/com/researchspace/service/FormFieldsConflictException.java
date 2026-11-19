package com.researchspace.service;

/**
 * Rejects a form field save because the field list changed after the editor read it, for example
 * through a save in another tab. Unchecked, so the manager's transaction rolls back.
 */
public class FormFieldsConflictException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  /**
   * @param message a resolved, user-facing message
   */
  public FormFieldsConflictException(String message) {
    super(message);
  }
}
