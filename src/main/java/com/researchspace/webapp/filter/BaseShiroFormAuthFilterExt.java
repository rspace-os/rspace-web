package com.researchspace.webapp.filter;

import com.researchspace.auth.LoginAuthorizer;
import com.researchspace.auth.LoginHelper;
import com.researchspace.auth.MaintenanceLoginAuthorizer;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * This class overrides the default login filter to record the login date in the User table and
 * perform other application-specific operations. <br>
 * This class is a base-class for all deployment types, and includes utility methods and
 * authentication-related methods common to all subclasses.
 *
 * <p>The type of subclass used at runtime is defined in BaseConfig.
 */
@Component
@Slf4j
public class BaseShiroFormAuthFilterExt extends FormAuthenticationFilter {

  // this is configured to use a different log file in log4j2.xml and not append to console
  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);
  protected static final String REDIRECT_FOR_BLOCKED = "/public/awaitingAuthorisation";

  protected @Autowired IPropertyHolder properties;
  protected @Autowired UserManager userMgr;
  private @Autowired List<LoginAuthorizer> loginAuthorizers = new ArrayList<>();

  @Autowired
  @Qualifier("manualLoginHelper")
  private LoginHelper loginHelper;

  @Autowired protected MaintenanceLoginAuthorizer maintenanceLoginAuthorizer;

  protected static final String ADMIN_LOGIN_URL = "/adminLogin";
  static final String ADMIN_LOGIN_REQUEST_PARAM = "adminLogin";

  @Override
  protected boolean isAccessAllowed(
      ServletRequest request, ServletResponse response, Object mappedValue) {
    boolean isAuthenticated = super.isAccessAllowed(request, response, mappedValue);
    if (isAuthenticated) {
      redirectLoginPageRequestToWorkspace(request, response);
      return true;
    }

    // for users who are not yet logged in, check if there is no maintenance in progress
    try {
      maintenanceLoginAuthorizer.isLoginPermitted(request, response, null);
    } catch (IOException e) {
      log.warn("exception on checking maintenanceLoginAuthorizer", e);
    }
    return false;
  }

  /**
   * @return whether user was redirected or not
   */
  protected boolean redirectLoginPageRequestToWorkspace(
      ServletRequest request, ServletResponse response) {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    if (httpRequest.getRequestURL().toString().indexOf("/login") >= 0) {
      User authenticated = userMgr.getAuthenticatedUserInSession();
      if (authenticated != null && authenticated.isAnonymousGuestAccount()) {
        SecurityUtils.getSubject().logout();
        return false;
      }
      // if we're going to login-page, but are already authenticated, we redirect to workspace
      // unless its the anonymous user - we want a real user who has been using public links
      // to be able to login as themselves in that case
      else {
        redirectToUrl(request, response, "/workspace");
        return true;
      }
    }
    return false;
  }

  private void redirectToUrl(ServletRequest request, ServletResponse response, String redirectUrl) {
    if (!isResponseAlreadyRedirected(response)) {
      try {
        WebUtils.issueRedirect(request, response, redirectUrl);
      } catch (IOException ex) {
        log.warn("Exception on redirect attempt to url " + redirectUrl, ex);
      }
    }
  }

  /**
   * Extends default login mechanism to handle
   *
   * <ul>
   *   <li>Attempts to login to a blocked or disabled account
   *   <li>Saving last login date for user.
   * </ul>
   */
  @Override
  protected boolean onLoginSuccess(
      AuthenticationToken token, Subject subject, ServletRequest request, ServletResponse response)
      throws Exception {

    User user = userMgr.getUserByUsername((String) subject.getPrincipal());
    for (LoginAuthorizer authorizer : loginAuthorizers) {
      if (!authorizer.isLoginPermitted(request, response, user)) {
        return false;
      }
    }
    doPostLogin(WebUtils.toHttp(request), user);
    return super.onLoginSuccess(token, subject, request, response);
  }

  /** To be called only once a user is confirmed as enabled, authenticated, etc. */
  protected HttpSession doPostLogin(HttpServletRequest request, User user) {
    return loginHelper.postLogin(user, request);
  }

  protected boolean isResponseAlreadyRedirected(ServletResponse response) {
    return ((HttpServletResponse) response).containsHeader("Location");
  }

  protected boolean isAdminLogin(ServletRequest request) {
    return request.getParameter(ADMIN_LOGIN_REQUEST_PARAM) != null;
  }

  @Override
  protected void redirectToLogin(ServletRequest request, ServletResponse response)
      throws IOException {
    if (isAdminLogin(request)) {
      WebUtils.issueRedirect(request, response, ADMIN_LOGIN_URL);
      return;
    }
    super.redirectToLogin(request, response);
  }

  /* ======================
   *  for tests
   *  =====================
   */

  protected void setUserMgr(UserManager userMgr) {
    this.userMgr = userMgr;
  }

  void setLoginAuthorizers(List<LoginAuthorizer> loginAuthorizers) {
    this.loginAuthorizers = loginAuthorizers;
  }

  protected void setMaintenanceLoginAuthorizer(MaintenanceLoginAuthorizer maintAuthorizer) {
    this.maintenanceLoginAuthorizer = maintAuthorizer;
  }

  protected void setLoginHelper(LoginHelper loginHelper) {
    this.loginHelper = loginHelper;
  }

  protected void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }
}
