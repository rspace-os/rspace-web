package com.researchspace.service;

/** A general exception that may be thrown during user signup process. */
public class UserSignupException extends Exception {

  private static final long serialVersionUID = 2250406903144959573L;

  public UserSignupException(String msg) {
    super(msg);
  }

  public UserSignupException(Throwable cause) {
    super(cause);
  }

  public UserSignupException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
