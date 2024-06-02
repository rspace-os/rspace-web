package com.researchspace.webapp.filter;

import java.util.Collections;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 * Policy for getting a remote User. Typically, this interface will be used in SSO environments
 * where the remote user will be obtained by a prior authentication step outside of RSpace.
 */
public interface RemoteUserRetrievalPolicy {

  /**
   * Dummy password which is unused for SSO login, but necessary because the system requires all
   * users to have a password
   */
  String SSO_DUMMY_PASSWORD = "user1234";

  /**
   * Supplied with an {@link HttpServletRequest}, this method will attempt to extract the remote
   * user name.
   *
   * @param request
   * @return The remote user name, or <code>null</code>if this information was not available.
   */
  String getRemoteUser(HttpServletRequest request);

  /**
   * Because the database requires a password for non SSO environments, in most SSO implementations
   * this will return a dummy placeholder value, since RSpace will never actually know the real SSO
   * password (and nor should we).
   *
   * <p>For signing/verification password, see details of RSPAC-1206
   *
   * @return A String password value
   */
  String getPassword();

  /**
   * Get a map of additional remote attributes Default behaviour is to return an immutable empty map
   */
  default Map<String, String> getOtherRemoteAttributes(HttpServletRequest httpRequest) {
    return Collections.emptyMap();
  }
}
