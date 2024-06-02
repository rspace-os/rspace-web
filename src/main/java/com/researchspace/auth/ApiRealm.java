package com.researchspace.auth;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;

/**
 * Requires an ApiKeyAuthenticationToken to validate, will always return true.
 *
 * <p>Authentication of the user token takes place outside of this class so that the user object can
 * be used in the controller without additional look up from the principal user name.
 */
public class ApiRealm extends RSpaceRealm {
  public static final String API_REALM_NAME = "API";

  public ApiRealm() {
    setAuthenticationTokenClass(ApiKeyAuthenticationToken.class);
    setCredentialsMatcher(new AllowAllCredentialsMatcher());
    setName(API_REALM_NAME);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    token = (ApiKeyAuthenticationToken) token;
    return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
  }

  @Override
  public boolean isAuthenticationCachingEnabled() {
    return true;
  }
}
