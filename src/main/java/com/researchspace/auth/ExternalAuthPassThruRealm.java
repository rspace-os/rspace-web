package com.researchspace.auth;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;

/** Requires an EmailAuthenticationToken to validate, will always return true. */
public class ExternalAuthPassThruRealm extends RSpaceRealm {
  public static final String EXT_OAUTH_REAM_NAME = "OAUTH";

  public ExternalAuthPassThruRealm() {
    setAuthenticationTokenClass(EmailAuthenticationToken.class);
    setCredentialsMatcher(new AllowAllCredentialsMatcher());
    setName(EXT_OAUTH_REAM_NAME);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    token = (EmailAuthenticationToken) token;
    return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
  }
}
