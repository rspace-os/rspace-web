package com.researchspace.auth;

/**
 * An exception that may be thrown during login process if user tries to login with their username
 * while they should be logging with usernameAlias.
 */
public class LoginWithUsernameNotAliasException extends Exception {

  private static final long serialVersionUID = -5774530659932725829L;

  public LoginWithUsernameNotAliasException(String msg) {
    super(msg);
  }
}
