package com.researchspace.auth;

import com.researchspace.model.oauth.OAuthToken;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;

/** Realm for authenticating users who use OAuth to access the API */
public class OAuthRealm extends RSpaceRealm {
  public static final String REALM_NAME = "OAUTH";

  public OAuthRealm() {
    setAuthenticationTokenClass(OAuthToken.class);
    setCredentialsMatcher(new AllowAllCredentialsMatcher());
    setName(REALM_NAME);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
  }

  @Override
  public boolean isAuthenticationCachingEnabled() {
    return true;
  }
}
