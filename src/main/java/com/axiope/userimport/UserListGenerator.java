package com.axiope.userimport;

import java.io.InputStream;

/** Interface to get new Users from any source */
public interface UserListGenerator {

  /**
   * Get a set of users with vaild usernames and emails that can be persisted to the DB. The
   * following fields of User need to be set :
   *
   * <ul>
   *   <li>Email, username, password, first and last name, and a Role - a Role defined in {@link
   *       Constants}.
   * </ul>
   *
   * @param inputStream input stream for the source of user data
   * @return A UserImportResult
   * @throws IllegalArgumentException if inputStream is not set
   */
  UserImportResult getUsersToSignup(InputStream inputStream);
}
