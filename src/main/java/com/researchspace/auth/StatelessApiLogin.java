package com.researchspace.auth;

import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.Subject;

/**
 * Thread-local mark for logins that must not disturb the session they arrived with.
 *
 * <p>Since Shiro 3, a login by a subject that already has a session rotates the session id (see
 * {@code DefaultSecurityManager#beforeSuccessfulLogin}). That is desirable for form logins but not
 * for the REST API, which performs a fresh {@code subject.login} on every request: an API request
 * carrying both an API key and a valid session cookie would rotate, and so invalidate, the browser
 * session it arrived with, silently logging that browser out. API requests are stateless by design
 * (their filter chains use noSessionCreation), so the rotation protects nothing there.
 *
 * <p>The API authenticator performs its logins through {@link #login(Subject,
 * AuthenticationToken)}, which marks the calling thread for the duration of the login; the security
 * managers consult {@link #isInProgress()} in their {@code beforeSuccessfulLogin} overrides and
 * keep the session id while the mark is set. A thread-local mark rather than a request attribute so
 * that it also works for non-web subjects, such as those created by the {@code
 * DefaultSecurityManager} used in test wiring.
 */
public final class StatelessApiLogin {

  private static final ThreadLocal<Boolean> IN_PROGRESS = new ThreadLocal<>();

  private StatelessApiLogin() {}

  /**
   * Performs a login without disturbing any session the subject already has. The mark spans only
   * the {@link Subject#login(AuthenticationToken)} call.
   */
  public static void login(Subject subject, AuthenticationToken token) {
    boolean outermost = !isInProgress();
    IN_PROGRESS.set(Boolean.TRUE);
    try {
      subject.login(token);
    } finally {
      if (outermost) {
        IN_PROGRESS.remove();
      }
    }
  }

  /**
   * True while the current thread is inside a {@link #login(Subject, AuthenticationToken)} call.
   */
  public static boolean isInProgress() {
    return IN_PROGRESS.get() != null;
  }
}
