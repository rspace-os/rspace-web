package com.researchspace.auth;

import com.researchspace.slack.SlackAuthToken;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;

public class SlackRealm extends RSpaceRealm {
  public static final String SLACK_REALM_NAME = "SLACK_REALM";

  public SlackRealm() {
    setAuthenticationTokenClass(SlackAuthToken.class);
    setCredentialsMatcher(new AllowAllCredentialsMatcher());
    setName(SLACK_REALM_NAME);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    return new SimpleAuthenticationInfo(token.getPrincipal(), token.getCredentials(), getName());
  }
}
