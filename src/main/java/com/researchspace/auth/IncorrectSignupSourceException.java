package com.researchspace.auth;

/**
 * An exception that may be thrown during login process if user signup source doesn't let them being
 * logged with a used realm.
 */
public class IncorrectSignupSourceException extends Exception {

  private static final long serialVersionUID = -5774530659932723040L;

  public IncorrectSignupSourceException(String msg) {
    super(msg);
  }
}
