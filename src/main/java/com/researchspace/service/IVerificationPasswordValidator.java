package com.researchspace.service;

import com.researchspace.model.User;

public interface IVerificationPasswordValidator {
  /**
   * Checks if user's verification password has been set to a valid value, if it is required. <br>
   *
   * @param The principal user
   * @return true if current verification password is valid or not needed ( because user is not an
   *     sso or Google user), false otherwise.
   */
  boolean isVerificationPasswordSet(User subject);

  /**
   * Checks a candidate verification password through the shared bounded verifier. Nothing is
   * written: a {@code {bcrypt}} value set before RSDEV-894 stays as it is.
   *
   * @param passwordOwner The password owner - can be the subject or an operating-as sysadmin
   * @param password Candidate password
   * @return true if password matches hashed value, false otherwise
   * @throws com.researchspace.auth.password.LoginVerificationBusyException if no verification slot
   *     is free in time
   */
  boolean authenticateVerificationPassword(User passwordOwner, String password);

  /**
   * Hashes verification password for storage.
   *
   * @param Plain text password
   * @return Hashed value of password
   */
  String hashVerificationPassword(String password);

  /**
   * Boolean test for whether a verification password is required for the <code>subject </code>
   *
   * @param subject
   * @return
   */
  boolean isVerificationPasswordRequired(User subject);
}
