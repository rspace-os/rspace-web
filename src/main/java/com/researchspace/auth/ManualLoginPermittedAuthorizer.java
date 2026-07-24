package com.researchspace.auth;

import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;

/**
 * This blocks manual login for situations where manual login is not allowed, perhaps depending on
 * product type or signup mechanism.
 */
public class ManualLoginPermittedAuthorizer extends AbstractLoginAuthorizer
    implements LoginAuthorizer {

  @Override
  public boolean isLoginPermitted(ServletRequest request, ServletResponse response, User user)
      throws IOException {
    if (SignupSource.GOOGLE.equals(user.getSignupSource())
        || SignupSource.SSO.equals(user.getSignupSource())) {
      logoutAndRedirect(request, response, "/login");
      return false;
    } else {
      return true;
    }
  }
}
