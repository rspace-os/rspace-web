package com.researchspace.auth;

import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import java.io.IOException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;

/** Prevents further login if account is locked or disabled */
public class AccountEnabledAuthorizer extends AbstractLoginAuthorizer implements LoginAuthorizer {

  public static final String REDIRECT_FOR_DISABLED = "/public/accountDisabled";
  protected static final String REDIRECT_FOR_AWAITING_EMAIL_CONFIRMATION =
      "/cloud/resendConfirmationEmail/awaitingEmailConfirmation?email=";

  private @Autowired(required = false) IPropertyHolder properties;

  @Override
  public boolean isLoginPermitted(ServletRequest request, ServletResponse response, User subject)
      throws IOException {
    if (subject.isLoginDisabled()) {
      HttpServletRequest req = WebUtils.toHttp(request);
      SECURITY_LOG.warn(
          "Attempt by [{}] to access disabled account from {}",
          subject.getUsername(),
          getRemoteAddress(req));
      if (subject.isAccountAwaitingEmailConfirmation()
          && properties != null
          && properties.isCloud()) {
        logoutAndRedirect(
            request, response, REDIRECT_FOR_AWAITING_EMAIL_CONFIRMATION + subject.getEmail());
      } else {
        logoutAndRedirect(request, response, REDIRECT_FOR_DISABLED);
      }
      return false;
    }
    return true;
  }
}
