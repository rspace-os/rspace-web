package com.researchspace.auth;

import jakarta.servlet.ServletRequest;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.subject.support.WebDelegatingSubject;

/**
 * A {@link DefaultWebSecurityManager} that skips session-fixation protection for stateless API
 * logins.
 *
 * <p>Since Shiro 3, a login by a subject that already has an HTTP session rotates the session id
 * (see {@code DefaultWebSecurityManager#beforeSuccessfulLogin}). That is desirable for form logins
 * but not for the REST API, which performs a fresh {@code subject.login} on every request: an API
 * request carrying both an API key and a valid session cookie would rotate, and so invalidate, the
 * browser session it arrived with, silently logging that browser out. API requests are stateless by
 * design (their filter chains use noSessionCreation), so the rotation protects nothing there.
 *
 * <p>The API authenticator marks such requests with the {@link #STATELESS_API_LOGIN} request
 * attribute; marked logins keep the session id they arrived with.
 */
public class ApiAwareWebSecurityManager extends DefaultWebSecurityManager {

  /** Request attribute marking the current login as a stateless API login. */
  public static final String STATELESS_API_LOGIN =
      ApiAwareWebSecurityManager.class.getName() + ".STATELESS_API_LOGIN";

  @Override
  protected void beforeSuccessfulLogin(Subject subject) {
    if (isStatelessApiLogin(subject)) {
      return;
    }
    super.beforeSuccessfulLogin(subject);
  }

  /** True if the subject's current request is marked as a stateless API login. */
  public static boolean isStatelessApiLogin(Subject subject) {
    if (subject instanceof WebDelegatingSubject webSubject) {
      ServletRequest request = webSubject.getServletRequest();
      return request != null && request.getAttribute(STATELESS_API_LOGIN) != null;
    }
    return false;
  }
}
