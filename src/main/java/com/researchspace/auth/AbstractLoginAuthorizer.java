package com.researchspace.auth;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.permissions.SecurityLogger;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides helper methods for implementations of LoginAuthorizer */
abstract class AbstractLoginAuthorizer {

  // this is configured to use a different log file in log4j2.xml and not append to console
  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  /**
   * Logs out the current subject and issues a redirect to the page provide by <code> redirectURL
   * </code>
   *
   * @param request
   * @param response
   * @param redirectURL
   * @throws IOException
   */
  protected void logoutAndRedirect(
      ServletRequest request, ServletResponse response, String redirectURL) throws IOException {
    SecurityUtils.getSubject().logout();
    WebUtils.issueRedirect(request, response, redirectURL, null);
  }

  protected String getRemoteAddress(HttpServletRequest req) {
    return RequestUtil.remoteAddr(req);
  }
}
