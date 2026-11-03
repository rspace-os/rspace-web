package com.researchspace.service;

/** The answer to a password check for an already logged-in user. */
public enum ReauthenticationResult {
  OK,
  WRONG,
  /** The password verifier was too busy to check the password; it is neither right nor wrong. */
  BUSY;

  public static ReauthenticationResult of(boolean matched) {
    return matched ? OK : WRONG;
  }
}
