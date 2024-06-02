package com.researchspace.auth;

import org.apache.shiro.authc.AuthenticationToken;

/** Should just be used after external authentication e.g by external auth provider. */
public class EmailAuthenticationToken implements AuthenticationToken {

  /** */
  private static final long serialVersionUID = -1053500239276332847L;

  private String email;
  private String storedUsername;

  public EmailAuthenticationToken(String email, String storedUsername) {
    super();
    this.email = email;
    this.storedUsername = storedUsername;
  }

  @Override
  public Object getPrincipal() {
    return storedUsername;
  }

  @Override
  public Object getCredentials() {
    return email;
  }
}
