package com.researchspace.webapp.filter;

import com.researchspace.auth.IncorrectSignupSourceException;
import com.researchspace.auth.SidVerificationException;
import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.auth.password.SentinelPasswordCheck;
import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserSignupException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;

@Slf4j
public class StandaloneShiroFormAuthFilterExt extends BaseShiroFormAuthFilterExt {

  @Autowired private IUserAccountLockoutPolicy lockoutPolicy;

  @Autowired private RemoteUserRetrievalPolicy remoteUserPolicy;

  @Autowired private MessageSourceUtils messages;

  @Autowired private BoundedPasswordVerifier verifier;

  @Autowired private SentinelPasswordCheck sentinelCheck;

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
        String msg = messages.getMessage("errors.ajax.unauthenticated.refreshRequired");
        response.getWriter().append(msg);
      } else if (!isResponseAlreadyRedirected(response)) {
        saveRequestAndRedirectToLogin(request, response);
      }
      return false;
    }

    if (!isLoginSubmission(request, response)) {
      return checkLockoutThenContinue(request, response);
    }
    try {
      return verifier.runExclusive(
          wholeLoginKey(userMgr.loginLockKey(getUsername(request))),
          () -> checkLockoutThenContinue(request, response));
    } catch (LoginVerificationBusyException e) {
      logBusyRefusal(request, getUsername(request), e);
      if (isAdminLogin(request)) {
        return redirectAdminLogin(request, response, e.getClass().getSimpleName());
      }
      setFailureAttribute(request, e);
      return true;
    }
  }

  /** NUL cannot appear in a username, so this lock never shares an entry with an inner lock. */
  static String wholeLoginKey(String usernameWeight) {
    return "\0login:" + usernameWeight;
  }

  private boolean checkLockoutThenContinue(ServletRequest request, ServletResponse response)
      throws Exception {
    // check if account isn't temporarily locked due to wrong password attempts (RSPAC-2265)
    try {
      String username = getUsername(request);
      User u = userMgr.getUserByUsernameOrAlias(username);
      if (u.isAccountLocked()
          && u.getLoginFailure() != null
          && !lockoutPolicy.isAfterLockoutTime(u)) {
        String password = getPassword(request);
        if (isLoginSubmission(request, response) && password != null) {
          sentinelCheck.pad(userMgr.loginLockKey(username), password);
        }
        setFailureAttribute(request, new AuthenticationException());
        SECURITY_LOG.warn(
            "Attempt to log in as [{}], from {}, but the account is temporarily locked",
            username,
            RequestUtil.remoteAddr(WebUtils.toHttp(request)));
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

    User u = userMgr.getUserByUsernameOrAlias(getUsername(request));
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
              "Login failure by [%s] from %s",
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
      } else if (e instanceof LoginVerificationBusyException busy) {
        // a flood, not a wrong password: counting it would let a flood lock users out
        logBusyRefusal(request, username, busy);
      } else {
        try {
          User u = userMgr.getUserByUsernameOrAlias(username);
          lockoutPolicy.handleLockoutOnFailure(u);
          userMgr.save(u);
        } catch (DataAccessException re) {
          SECURITY_LOG.warn(
              "Login attempt for username [{}] who cannot be found in RSpace, from {}",
              username,
              RequestUtil.remoteAddr(WebUtils.toHttp(request)));
        }
      }
    }

    if (isAdminLogin(request)) {
      boolean isSignupSourceEx =
          (e != null) && (e.getCause() instanceof IncorrectSignupSourceException);
      String loginException =
          isSignupSourceEx ? "IncorrectSignupSourceException" : e.getClass().getSimpleName();
      redirectAdminLogin(request, response, loginException);
    }

    return super.onLoginFailure(token, e, request, response);
  }

  private void logBusyRefusal(
      ServletRequest request, String username, LoginVerificationBusyException e) {
    SECURITY_LOG.warn(
        "Login by [{}] from {} refused: {}",
        username,
        RequestUtil.remoteAddr(WebUtils.toHttp(request)),
        e.getMessage());
  }

  private boolean redirectAdminLogin(
      ServletRequest request, ServletResponse response, String loginException) {
    try {
      WebUtils.issueRedirect(
          request,
          response,
          ADMIN_LOGIN_URL,
          Collections.singletonMap("loginException", loginException));
    } catch (IOException ioe) {
      log.warn("Exception on attempt to redirect to admin login", ioe);
    }
    return false;
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

  protected void setVerifier(BoundedPasswordVerifier verifier) {
    this.verifier = verifier;
  }

  protected void setSentinelCheck(SentinelPasswordCheck sentinelCheck) {
    this.sentinelCheck = sentinelCheck;
  }
}
