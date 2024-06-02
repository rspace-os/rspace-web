package com.researchspace.auth;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;

/** Test-specific class that reveals information about authentication process. */
public class ApiRealmTestSpy extends ApiRealm {

  boolean doGetInfoCalled = false;

  public boolean isDoGetInfoCalled() {
    return doGetInfoCalled;
  }

  // this will only get called if cache lookup fails
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    AuthenticationInfo info = super.doGetAuthenticationInfo(token);
    doGetInfoCalled = true;
    return info;
  }

  // resets test spy attributes to original state
  protected final void reset() {
    doGetInfoCalled = false;
  }
}
