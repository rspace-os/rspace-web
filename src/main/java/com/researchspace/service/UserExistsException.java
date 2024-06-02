package com.researchspace.service;

public class UserExistsException extends Exception {
  private static final long serialVersionUID = 4050482305178810162L;

  private boolean existingUsername;

  private boolean existingEmail;

  /**
   * Constructor for UserExistsException.
   *
   * @param message exception message
   */
  public UserExistsException(final String message) {
    super(message);
  }

  public boolean isExistingUsername() {
    return existingUsername;
  }

  public void setExistingUsername(boolean existingUsername) {
    this.existingUsername = existingUsername;
  }

  public boolean isExistingEmail() {
    return existingEmail;
  }

  public void setExistingEmail(boolean existingEmail) {
    this.existingEmail = existingEmail;
  }
}
