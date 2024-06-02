package com.researchspace.auth;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;

/**
 * Requires an SSO authentication token. Will always authenticate as uses <code>
 * AllowAllCredentialsMatcher</code> , assuming authentication has already happened.
 */
public class SSOPassThruRealm extends RSpaceRealm {
  public static final String SSO_REALM_NAME = "SSO";

  public SSOPassThruRealm() {
    setAuthenticationTokenClass(SSOAuthenticationToken.class);
    setCredentialsMatcher(new AllowAllCredentialsMatcher());
    setName(SSO_REALM_NAME);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    token = (SSOAuthenticationToken) token;
    return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
  }
}
