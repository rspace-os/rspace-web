package com.researchspace.auth;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;

/**
 * A {@link DefaultWebSecurityManager} that skips session-fixation protection while a {@link
 * StatelessApiLogin} is in progress, so API requests keep the session id they arrived with. See
 * {@link StatelessApiLogin} for why.
 */
public class ApiAwareWebSecurityManager extends DefaultWebSecurityManager {

  @Override
  protected void beforeSuccessfulLogin(Subject subject) {
    if (StatelessApiLogin.isInProgress()) {
      return;
    }
    super.beforeSuccessfulLogin(subject);
  }
}
