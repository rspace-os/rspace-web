package com.researchspace.auth;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;

/**
 * A {@link DefaultWebSecurityManager} that skips session-fixation protection for stateless API
 * logins.
 *
 * <p>Since Shiro 3, a login by a subject that already has a session rotates the session id (see
 * {@code DefaultSecurityManager#beforeSuccessfulLogin}). That is desirable for form logins but not
 * for the REST API, which performs a fresh {@code subject.login} on every request: an API request
 * carrying both an API key and a valid session cookie would rotate, and so invalidate, the browser
 * session it arrived with, silently logging that browser out. API requests are stateless by design
 * (their filter chains use noSessionCreation), so the rotation protects nothing there.
 *
 * <p>The API authenticator performs such logins through {@link #doStatelessLogin(Runnable)}, which
 * marks the calling thread for the duration of the login; marked logins keep the session id they
 * arrived with. A thread-local mark rather than a request attribute so that it also works for
 * non-web subjects, such as those created by the {@code DefaultSecurityManager} used in test
 * wiring.
 */
public class ApiAwareWebSecurityManager extends DefaultWebSecurityManager {

  private static final ThreadLocal<Boolean> STATELESS_API_LOGIN = new ThreadLocal<>();

  /**
   * Runs a login that must not disturb any session the current thread's subject already has. The
   * mark only spans the given callback, which should contain just the {@code subject.login} call.
   */
  public static void doStatelessLogin(Runnable login) {
    Boolean previous = STATELESS_API_LOGIN.get();
    STATELESS_API_LOGIN.set(Boolean.TRUE);
    try {
      login.run();
    } finally {
      if (previous == null) {
        STATELESS_API_LOGIN.remove();
      } else {
        STATELESS_API_LOGIN.set(previous);
      }
    }
  }

  /** True if the current thread is inside a {@link #doStatelessLogin(Runnable)} call. */
  public static boolean isStatelessApiLogin() {
    return STATELESS_API_LOGIN.get() != null;
  }

  @Override
  protected void beforeSuccessfulLogin(Subject subject) {
    if (isStatelessApiLogin()) {
      return;
    }
    super.beforeSuccessfulLogin(subject);
  }
}
