package com.researchspace.auth;

import com.researchspace.model.User;
import java.io.IOException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Performs ip white-list validation for sysadmin, if set. */
public class IPWhitelistLoginAuthorizer extends AbstractLoginAuthorizer implements LoginAuthorizer {

  private @Autowired WhiteListIPChecker ipChecker;

  static final String REDIRECT_FOR_IP_FAILURE = "/public/ipAddressInvalid";
  private Logger log = LoggerFactory.getLogger(IPWhitelistLoginAuthorizer.class);

  @Override
  public boolean isLoginPermitted(ServletRequest request, ServletResponse response, User user)
      throws IOException {
    boolean ipOk = ipChecker.isRequestWhitelisted(request, user, SECURITY_LOG);

    if (!ipOk) {
      SECURITY_LOG.warn(
          "Attempt by [{}] to login as sysadmin from invalid IP address {}",
          user.getUsername(),
          getRemoteAddress(WebUtils.toHttp(request)));
      logoutAndRedirect(request, response, REDIRECT_FOR_IP_FAILURE);
    }
    return ipOk;
  }
}
