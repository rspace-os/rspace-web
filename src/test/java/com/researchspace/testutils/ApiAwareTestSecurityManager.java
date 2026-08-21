package com.researchspace.testutils;

import com.researchspace.auth.ApiAwareWebSecurityManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;

/**
 * Test-profile mirror of {@link ApiAwareWebSecurityManager}: stateless API logins never disturb a
 * session that arrived with the request. The core {@link DefaultSecurityManager} used in test
 * wiring performs no session-fixation rotation today, so this class only keeps the test wiring
 * structurally identical to run/prod.
 */
public class ApiAwareTestSecurityManager extends DefaultSecurityManager {

  @Override
  protected void beforeSuccessfulLogin(Subject subject) {
    if (ApiAwareWebSecurityManager.isStatelessApiLogin(subject)) {
      return;
    }
    super.beforeSuccessfulLogin(subject);
  }
}
