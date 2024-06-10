package com.researchspace.service;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserExistsException extends Exception {
  private static final long serialVersionUID = 4050482305178810135L;

  private boolean existingUsername;

  private boolean existingUsernameAlias;

  private boolean existingEmail;

  public UserExistsException(final String message) {
    super(message);
  }

  public UserExistsException(
      final String message,
      boolean existingUsername,
      boolean existingUsernameAlias,
      boolean existingEmail) {
    super(message);
    this.existingUsername = existingUsername;
    this.existingUsernameAlias = existingUsernameAlias;
    this.existingEmail = existingEmail;
  }
}
