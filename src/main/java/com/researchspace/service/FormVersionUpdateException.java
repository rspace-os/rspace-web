package com.researchspace.service;

/**
 * A form's draft could not be made its new version, for example because the form is a system form.
 * Unchecked, so a manager transaction that has already saved the draft rolls back.
 */
public class FormVersionUpdateException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  /**
   * @param message a resolved, user-facing message
   */
  public FormVersionUpdateException(String message) {
    super(message);
  }
}
