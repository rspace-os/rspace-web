package com.researchspace.auth;

import org.apache.shiro.authc.AuthenticationToken;

/** Should just be used after external authentication by SSO mechanism such as EASE or Shibboleth */
public class SSOAuthenticationToken implements AuthenticationToken {

  /** */
  private static final long serialVersionUID = -1053510239276332847L;

  private String ssoUsername;

  public SSOAuthenticationToken(String ssoUsername) {
    super();
    this.ssoUsername = ssoUsername;
  }

  @Override
  public Object getPrincipal() {
    return ssoUsername;
  }

  @Override
  public Object getCredentials() {
    return ssoUsername;
  }
}
