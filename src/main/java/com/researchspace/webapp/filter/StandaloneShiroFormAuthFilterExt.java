package com.researchspace.webapp.filter;

import com.researchspace.auth.IncorrectSignupSourceException;
import com.researchspace.auth.SidVerificationException;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserSignupException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

public class StandaloneShiroFormAuthFilterExt extends BaseShiroFormAuthFilterExt {

  @Autowired private IUserAccountLockoutPolicy lockoutPolicy;

  @Autowired private RemoteUserRetrievalPolicy remoteUserPolicy;

  @Autowired private MessageSourceUtils messages;

  /**
   * Overrides standard method, to return an error response directly, if the request was an Ajax
   * request.
   */
  @Override
  protected boolean onAccessDenied(ServletRequest request, ServletResponse response)
      throws Exception {

    if (!isLoginRequest(request, response)) {
      if (SECURITY_LOG.isTraceEnabled()) {
        SECURITY_LOG.trace(
            "Attempting to access a path which requires authentication. Forwarding to the "
                + "Authentication url ["
                + getLoginUrl()
                + "]");
      }
      if (RequestUtil.isAjaxRequest(request)) {
        ((HttpServletResponse) response).setStatus(HttpStatus.FORBIDDEN.value());
        String msg = messages.getMessage("ajax.unauthenticated.msg");
        response.getWriter().append(msg);
      } else if (!isResponseAlreadyRedirected(response)) {
        saveRequestAndRedirectToLogin(request, response);
      }
      return false;
    }

    // check if account isn't temporarily locked due to wrong password attempts (RSPAC-2265)
    try {
      String username = getUsername(request);
      User u = userMgr.getUserByUsername(username);
      if (u.isAccountLocked()
          && u.getLoginFailure() != null
          && !lockoutPolicy.isAfterLockoutTime(u)) {
        setFailureAttribute(request, new AuthenticationException());
        SECURITY_LOG.info(
            "Attempt to log in as '" + username + "', but account is temporarily locked");
        return true; /* true as the request should continue, failureAttribute will take it back to login page */
      }
    } catch (DataAccessException re) {
      if (getUsername(request) != null
          && !properties.isUserSignup()
          && properties.isLdapAuthenticationEnabled()) {
        HttpSession session = ((HttpServletRequest) request).getSession();
        session.setAttribute("userName", getUsername(request));
        WebUtils.issueRedirect(request, response, "/public/noldapsignup", null);
      }
    }

    return super.onAccessDenied(request, response);
  }

  /** Overrides to assure the account is unlocked with first login attempt after lockout */
  @Override
  protected boolean onLoginSuccess(
      AuthenticationToken token, Subject subject, ServletRequest request, ServletResponse response)
      throws Exception {

    User u = userMgr.getUserByUsername(getUsername(request));
    if (u.isAccountLocked() && u.getLoginFailure() != null) {
      lockoutPolicy.handleLockoutOnSuccess(u);
    }

    if (SignupSource.SSO_BACKDOOR.equals(u.getSignupSource())) {
      String remoteUser = getRemoteUserFromRequest(request);
      SECURITY_LOG.info(
          "Successful backdoor login as [{}] by SSO user [{}] ", u.getUsername(), remoteUser);
    }

    return super.onLoginSuccess(token, subject, request, response);
  }

  private String getRemoteUserFromRequest(ServletRequest request) {
    return remoteUserPolicy.getRemoteUser((HttpServletRequest) request);
  }

  /** Overrides default to include logging to security event log. */
  @Override
  protected boolean onLoginFailure(
      AuthenticationToken token,
      AuthenticationException e,
      ServletRequest request,
      ServletResponse response) {

    if (token.getPrincipal() != null) {
      String failureDetails =
          String.format(
              "Login failure by %s from %s",
              token.getPrincipal().toString(), RequestUtil.remoteAddr(WebUtils.toHttp(request)));
      if (properties.isSSO()) {
        failureDetails += " (SSO user [" + getRemoteUserFromRequest(request) + "])";
      }
      SECURITY_LOG.warn(failureDetails);

      String username = getUsername(request);
      boolean autoSignupProblem = (e != null) && (e.getCause() instanceof UserSignupException);
      boolean sidVerificationProblem =
          (e != null) && (e.getCause() instanceof SidVerificationException);
      if (autoSignupProblem || sidVerificationProblem) {
        WebUtils.toHttp(request).setAttribute("checkedExceptionMessage", e.getCause().getMessage());
      } else {
        try {
          User u = userMgr.getUserByUsername(username);
          lockoutPolicy.handleLockoutOnFailure(u);
          userMgr.save(u);
        } catch (DataAccessException re) {
          SECURITY_LOG.warn("User [" + username + "] can't be found in RSpace");
        }
      }
    }

    if (isAdminLogin(request)) {
      try {
        boolean isSignupSourceEx =
            (e != null) && (e.getCause() instanceof IncorrectSignupSourceException);
        String loginException =
            isSignupSourceEx ? "IncorrectSignupSourceException" : e.getClass().getSimpleName();
        Map<String, String> failureReason =
            Collections.singletonMap("loginException", loginException);
        WebUtils.issueRedirect(request, response, ADMIN_LOGIN_URL, failureReason);
      } catch (IOException ioe) {
        SECURITY_LOG.warn("Exception on attempt to redirect to admin login: " + ioe.getMessage());
      }
    }

    return super.onLoginFailure(token, e, request, response);
  }

  /*
   * =====================
   *     for tests
   * =====================
   */
  protected void setLockoutPolicy(IUserAccountLockoutPolicy lockoutPolicy) {
    this.lockoutPolicy = lockoutPolicy;
  }

  public void setMessages(MessageSourceUtils messageSourceUtils) {
    this.messages = messageSourceUtils;
  }
}
