package com.researchspace.netfiles;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;

public interface NfsAuthentication {

  /**
   * Only checks if required credentials are provided, without login attempt.
   *
   * @param nfsusername
   * @param nfspassword
   * @param user
   * @return null if provided credentials look valid, or error message code if provided credentials
   *     won't be enough for login attempt
   */
  String validateCredentials(String nfsusername, String nfspassword, User user);

  /** Creates NfsClient with provided credentials, without necessarily trying to log in. */
  NfsClient login(String nfsusername, String nfspassword, NfsFileSystem fileSystem, User user);

  /** Returns authentication error message matching given authentication type */
  String getMessageCodeForAuthException(NfsAuthException auth);
}
