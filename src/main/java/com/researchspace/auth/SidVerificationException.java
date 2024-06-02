package com.researchspace.auth;

/**
 * An exception that may be thrown during login process if User.sid value stored in RSpace is not
 * matching SID value from identity provider.
 */
public class SidVerificationException extends Exception {

  private static final long serialVersionUID = -3870584216622607898L;

  public SidVerificationException(String msg) {
    super(msg);
  }
}
