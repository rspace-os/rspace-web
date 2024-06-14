package com.researchspace.service;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserExistsException extends Exception {
  private static final long serialVersionUID = 4050482305178810135L;

  private boolean existingUsername;

  private boolean existingUsernameAlias;

  public UserExistsException(final String message) {
    super(message);
  }
}
