package com.researchspace.testutils;

import com.researchspace.auth.ApiAwareWebSecurityManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;

/**
 * Test-profile mirror of {@link ApiAwareWebSecurityManager} for the non-web {@link
 * DefaultSecurityManager} used in test wiring. Since Shiro 3 the core manager also rotates the
 * session id on every login; without this skip, an apiKey-authenticated MockMvc request would
 * rotate the test thread's session mid-test, silently invalidating session-keyed state such as
 * record edit locks.
 */
public class ApiAwareTestSecurityManager extends DefaultSecurityManager {

  @Override
  protected void beforeSuccessfulLogin(Subject subject) {
    if (ApiAwareWebSecurityManager.isStatelessApiLogin()) {
      return;
    }
    super.beforeSuccessfulLogin(subject);
  }
}
