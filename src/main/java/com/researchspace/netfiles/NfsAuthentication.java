package com.researchspace.netfiles;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsFileSystem;
import org.springframework.context.support.DefaultMessageSourceResolvable;

public interface NfsAuthentication {

  /**
   * Only checks if required credentials are provided, without login attempt.
   *
   * @param nfsusername
   * @param nfspassword
   * @param user
   * @return null if provided credentials look valid, or an error if they won't be enough for login
   */
  DefaultMessageSourceResolvable validateCredentials(
      String nfsusername, String nfspassword, User user);

  /** Creates NfsClient with provided credentials, without necessarily trying to log in. */
  NfsClient login(String nfsusername, String nfspassword, NfsFileSystem fileSystem, User user);

  /** Returns the authentication error matching the given authentication type. */
  DefaultMessageSourceResolvable getMessageForAuthException(NfsAuthException auth);
}
