package com.researchspace.auth.password;

import org.apache.shiro.authc.AuthenticationException;

/**
 * Password verification was refused because no verification slot became free in time. Not a wrong
 * password, so it must not count toward account lockout. The message names the username and is for
 * logs only; never show it to a user. Callers map this exception to a generic message.
 */
public class LoginVerificationBusyException extends AuthenticationException {

  public LoginVerificationBusyException(String message) {
    super(message);
  }
}
