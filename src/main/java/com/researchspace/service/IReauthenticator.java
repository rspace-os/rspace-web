package com.researchspace.service;

import com.researchspace.model.User;

/**
 * Reauthenticates an already-logged in user, or a SSO/Google user that is using a verification
 * password. Useful for authenticating secure actions such as signing, witnessing, changing profile
 * information, getting OAuth access tokens etc.
 */
public interface IReauthenticator {

  /**
   * Reauthenticates a user; or if is sysadmin operating as a user, sysadmin can reauthenticate with
   * his own password.
   *
   * @param subject The principal user - i.e., the subject, or whoever sysadmin is operating as.
   * @param pwd the password
   * @return {@link ReauthenticationResult#BUSY} when the password verifier refused the check as
   *     busy, so the caller can tell the user to retry instead of saying the password was wrong
   */
  ReauthenticationResult reauthenticate(User subject, String pwd);

  /**
   * Checks a user's own verification password, e.g. before changing it, with the same busy handling
   * and security logging as {@link #reauthenticate}. Unlike that method it never substitutes an
   * operating-as sysadmin.
   *
   * @param passwordOwner the user whose verification password is checked
   * @param pwd the candidate verification password
   */
  ReauthenticationResult reauthenticateWithVerificationPassword(User passwordOwner, String pwd);
}
