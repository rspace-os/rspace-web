package com.researchspace.ldap;

import com.researchspace.model.User;
import com.researchspace.service.UserSignupException;

/** Interface for LDAP interactions involving users */
public interface UserLdapRepo {

  User findUserByUsername(String username);

  /**
   * Checks whether username/password is valid for authenticating in LDAP.
   *
   * @return User object with basic information from LDAP, or null for invalid credentials
   */
  User authenticate(String username, String password);

  User signupLdapUser(User user) throws UserSignupException;

  /**
   * Queries LDAP for SID of provided user, then saves it in database.
   *
   * @param username of LDAP-authenticated user for whom SID should be retrieved
   * @return retrieved SID (or null, if user not found in LDAP)
   * @throws IllegalArgumentException if provided username doesn't point to LDAP-authenticated user
   *     or if user.sid field is already set
   */
  String retrieveSidForLdapUser(String username);
}
