package com.researchspace.auth;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;

/**
 * Realm for sysadmin-initiated actions during startup. This allows unauthenticated access by
 * sysadmin1 user
 */
public class GlobalInitSysadminRealm extends RSpaceRealm {
  public static final String REALM_NAME = "GLOBAL_INIT";

  public GlobalInitSysadminRealm() {
    setAuthenticationTokenClass(GlobalInitSysadminAuthenticationToken.class);
    setCredentialsMatcher(new AllowAllCredentialsMatcher());
    setName(REALM_NAME);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    token = (GlobalInitSysadminAuthenticationToken) token;
    return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
  }
}
