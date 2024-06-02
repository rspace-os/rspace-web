package com.researchspace.auth;

import com.researchspace.model.User;
import java.io.IOException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Strategy interface for additional authentication logic to determine if user should have access to
 * their account after login via Shiro.
 */
public interface LoginAuthorizer {

  /**
   * Implementations should perform the following operations if they determine that login should not
   * be allowed:
   *
   * <ul>
   *   <li>Logout the user (they are already logged in with Shiro, but this should be reversed here)
   *   <li>Set response header to point to an appropriate redirect error page
   *   <li>return <code>false</code>
   * </ul>
   *
   * Support for these operations is provided by {@link AbstractLoginAuthorizer}.
   *
   * @param request
   * @param response
   * @param user
   * @return <code>true</code> if login process should continue, <code>false</code> otherwise.
   * @throws IOException for IO errors writing to response
   */
  boolean isLoginPermitted(ServletRequest request, ServletResponse response, User user)
      throws IOException;
}
