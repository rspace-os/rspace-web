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
   */
  boolean reauthenticate(User subject, String pwd);
}
