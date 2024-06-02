package com.researchspace.auth.wopi;

import com.researchspace.auth.RSpaceRealm;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;

public class WopiRealm extends RSpaceRealm {
  public static final String WOPI_REALM_NAME = "WOPI_REALM";

  public WopiRealm() {
    setAuthenticationTokenClass(WopiAuthToken.class);
    setCredentialsMatcher(new AllowAllCredentialsMatcher());
    setName(WOPI_REALM_NAME);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
  }
}
