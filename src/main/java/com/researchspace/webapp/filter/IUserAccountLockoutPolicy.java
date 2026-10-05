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
   * Forcibly unlocks and resets user account regardless of previous login state. RSPAC-1974
   *
   * @param user
   */
  void forceUnlock(User user);
}
