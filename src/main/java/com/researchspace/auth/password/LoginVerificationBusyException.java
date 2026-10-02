package com.researchspace.auth.password;

import org.apache.shiro.authc.AuthenticationException;

/**
 * Password verification was refused because no verification slot became free in time. Not a wrong
 * password, so it must not count toward account lockout.
 */
public class LoginVerificationBusyException extends AuthenticationException {

  public LoginVerificationBusyException(String message) {
    super(message);
  }
}
