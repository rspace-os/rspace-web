package com.researchspace.webapp.filter;

import com.researchspace.model.User;

/**
 * Policy that decides whether or not to lock a user's account after a login failure. The argument
 * user may be modified if the account is locked
 */
public interface IUserAccountLockoutPolicy {

  void handleLockoutOnSuccess(User user);

  void handleLockoutOnFailure(User user);

  boolean isAfterLockoutTime(User u);

  /**
   * Counts a failed reauthentication (signing, password change, OAuth password grant etc.) with the
   * same counter and window as failed logins, but without setting the account-locked flag, which
   * API and SSO logins treat as disabled until a form login clears it (RSDEV-894).
   *
   * @param user the user whose password check failed; modified in place
   */
  void handleReauthenticationFailure(User user);

  /**
   * Whether reauthentication must be refused without checking the password: the account is locked,
   * or has reached the failure limit, and the lockout time has not passed.
   *
   * @param user the user reauthenticating
   */
  boolean isReauthenticationLocked(User user);

  /**
   * Forcibly unlocks and resets user account regardless of previous login state. RSPAC-1974
   *
   * @param user
   */
  void forceUnlock(User user);
}
