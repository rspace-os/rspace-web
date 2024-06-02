package com.researchspace.webapp.filter;

import com.researchspace.auth.LoginAuthorizer;
import com.researchspace.auth.SSOAuthenticationToken;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.webapp.controller.SignupController;
import java.io.IOException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;

/**
 * Handles authentication when we're in an SSO environment. Since we rely on an upstream
 * authenticator, this class uses a 'dummy' password and merely checks that the user exists.
 */
@Slf4j
public class SSOShiroFormAuthFilterExt extends BaseShiroFormAuthFilterExt {

  public static final String SSOINFO_URL = "/public/ssoinfo";

  public static final String SSOINFO_USERNAMECONFLICT_URL = "/public/ssoinfoUsernameConflict";

  public static final String REMOTE_USER_USERNAME_ATTR = "remoteUserUsername";

  private RemoteUserRetrievalPolicy remoteUserPolicy;

  @Autowired private StandaloneShiroFormAuthFilterExt standaloneShiroAuthFilter;
  private boolean standaloneShiroFilterReady = false;

  @Autowired
  @Qualifier("accountEnabledAuthorizer")
  private LoginAuthorizer accountEnabledAuthorizer;

  public SSOShiroFormAuthFilterExt(@Autowired RemoteUserRetrievalPolicy remoteUserPolicy) {
    this.remoteUserPolicy = remoteUserPolicy;
  }

  @Override
  protected boolean isAccessAllowed(
      ServletRequest request, ServletResponse response, Object mappedValue) {

    boolean isAuthenticated = super.isAccessAllowed(request, response, mappedValue);
    if (isAuthenticated) {
      redirectLoginPageRequestToWorkspace(request, response);
      return true;
    }
    return isRemoteUserAccessAllowed(request, response);
  }

  private boolean isRemoteUserAccessAllowed(ServletRequest request, ServletResponse response) {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    String remoteUser = remoteUserPolicy.getRemoteUser(httpRequest);

    if (remoteUser == null || remoteUser.length() < 2) {
      return false;
    }

    /* RSPAC-2691: be careful with session creation if response is already committed */
    if (!response.isCommitted()) {
      /* RSPAC-2189: UI may want to know sso username in case it's different from current user's username */
      HttpSession session = httpRequest.getSession();
      session.setAttribute(REMOTE_USER_USERNAME_ATTR, remoteUser);
    }

    if (isAdminLogin(request)) {
      /*
       * When using adminLogin we stop automatic login based on remoteUser early,
       * as user probably wants to login to their internal backdoor account, and
       * that would be rejected with redirect by the code a few lines below.
       */
      return false;
    }

    try {
      User user = userMgr.getUserByUsername(remoteUser);
      if (user == null) {
        return false;
      }

      // trying to log into backdoor account with SSO identity
      SignupSource userSignupSource = user.getSignupSource();
      if (SignupSource.SSO_BACKDOOR.equals(userSignupSource)) {
        redirectToAccountConflictPage(httpRequest, response);
        return false;
      }

      boolean maintenanceLogin =
          maintenanceLoginAuthorizer.isLoginPermitted(httpRequest, response, user);
      boolean userEnabled = accountEnabledAuthorizer.isLoginPermitted(request, response, user);
      if (maintenanceLogin && userEnabled) {
        AuthenticationToken token = new SSOAuthenticationToken(user.getUsername());
        Subject subject = SecurityUtils.getSubject();
        subject.login(token); // not call onLogSucess methods at all

        updateUserIfAccountDetailsChanged(httpRequest, user);
        doPostLogin(httpRequest, user);

        redirectLoginPageRequestToWorkspace(request, response);
        return true;
      }

    } catch (DataAccessException ex) {
      SECURITY_LOG.warn(ex.getMessage() + " User does not exist in RSpace DB: " + remoteUser);
      try {
        if (httpRequest.isRequestedSessionIdValid()) {
          httpRequest.getSession().setAttribute("userName", remoteUser);
        }
        redirectToNoAccountPage(httpRequest, response);
      } catch (IOException eex) {
        SECURITY_LOG.warn(eex.toString());
      }
    } catch (Exception e) {
      SECURITY_LOG.warn("Warning: {}", e.getMessage());
    }
    return false;
  }

  private void redirectToNoAccountPage(HttpServletRequest request, ServletResponse response)
      throws IOException {
    if (!isResponseAlreadyRedirected(response)) {
      if (properties.isUserSignup()) {
        WebUtils.issueRedirect(request, response, SignupController.SIGNUP_URL, null);
      } else {
        WebUtils.issueRedirect(request, response, SSOINFO_URL, null);
      }
    }
  }

  private void updateUserIfAccountDetailsChanged(HttpServletRequest request, User user) {
    // RSPAC-2588
    if (properties.isSSOSelfDeclarePiEnabled()) {
      String isAllowedPiRoleParam =
          remoteUserPolicy.getOtherRemoteAttributes(request).get("isAllowedPiRole");
      if (StringUtils.isEmpty(isAllowedPiRoleParam)) {
        log.info(
            "deployment property 'deployment.sso.selfDeclarePI.enabled' is set to true, "
                + "but 'isAllowedPiRole' request header is empty for user {}",
            user.getUsername());
      }
      boolean isAllowedPiRole = "true".equalsIgnoreCase(isAllowedPiRoleParam);
      log.debug(
          "incoming isAllowedPiRole param: '{}', saved user.isAllowedPiRole: '{}'",
          isAllowedPiRoleParam,
          user.isAllowedPiRole());
      if (isAllowedPiRole != user.isAllowedPiRole()) {
        log.info("updating isAllowedPiRole to {} for user {}", isAllowedPiRole, user.getUsername());
        user.setAllowedPiRole(isAllowedPiRole);
        userMgr.saveUser(user);
      }
    }
  }

  private void redirectToAccountConflictPage(HttpServletRequest request, ServletResponse response)
      throws IOException {
    WebUtils.issueRedirect(request, response, SSOINFO_USERNAMECONFLICT_URL);
  }

  @Override
  protected boolean onAccessDenied(ServletRequest request, ServletResponse response)
      throws Exception {
    if (isAdminLogin(request)) {
      return getStandaloneShiroAuthFilter().onAccessDenied(request, response);
    }

    /* if it's an SSO request, we've got here because isAccessAllowed returned false.
     * but in this case we've already redirected, so shouldn't redirect again. */
    return false;
  }

  @Override
  protected boolean onLoginSuccess(
      AuthenticationToken token, Subject subject, ServletRequest request, ServletResponse response)
      throws Exception {

    if (isAdminLogin(request)) {
      return getStandaloneShiroAuthFilter().onLoginSuccess(token, subject, request, response);
    }

    return super.onLoginSuccess(token, subject, request, response);
  }

  public StandaloneShiroFormAuthFilterExt getStandaloneShiroAuthFilter() {
    if (!standaloneShiroFilterReady) {
      standaloneShiroAuthFilter.setLoginUrl(getLoginUrl());
      standaloneShiroAuthFilter.setSuccessUrl(getSuccessUrl());
      standaloneShiroFilterReady = true;
    }
    return standaloneShiroAuthFilter;
  }

  /*
   * ================
   *   For testing
   * ================
   */
  protected void setAccountEnabledAuthorizer(LoginAuthorizer accountEnabledAuthorizer) {
    this.accountEnabledAuthorizer = accountEnabledAuthorizer;
  }
}
