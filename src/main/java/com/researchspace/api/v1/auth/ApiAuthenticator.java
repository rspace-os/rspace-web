package com.researchspace.api.v1.auth;

import com.researchspace.model.User;
import javax.servlet.http.HttpServletRequest;

/** Top-level interface to hide implementation details (OAUTH, key, shiro etc). */
public interface ApiAuthenticator {

  /**
   * @param request An {@link HttpServletRequest} containing User credentials in some format.
   * @return A {@link User} on successful authentication and authorisation
   * @throws ApiAuthenticationException if authorisation failed
   */
  User authenticate(HttpServletRequest request);

  /**
   * Performs any logout/clean up operations
   *
   * @apiNote Default implementation does nothing
   */
  default void logout() {}
}
