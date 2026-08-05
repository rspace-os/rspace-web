package com.researchspace.auth;

import com.researchspace.model.User;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.apache.shiro.web.util.WebUtils;

/** Prevents login if account requires authorisation after signup. */
public class AccountReviewRequiredAuthorizer extends AbstractLoginAuthorizer
    implements LoginAuthorizer {

  protected static final String REDIRECT_FOR_BLOCKED = "/public/awaitingAuthorisation";

  @Override
  public boolean isLoginPermitted(ServletRequest request, ServletResponse response, User user)
      throws IOException {
    if (user.isAccountLockedAwaitingAuthorisation()) {
      // known user, but account is locked
      SECURITY_LOG.warn(
          "Attempt by [{}] to login before account activated, from {}",
          user.getUsername(),
          getRemoteAddress(WebUtils.toHttp(request)));
      // make sure they can't access any resources
      logoutAndRedirect(request, response, REDIRECT_FOR_BLOCKED);
      return false;
    }
    return true;
  }
}
