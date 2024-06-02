package com.researchspace.auth;

import com.researchspace.Constants;
import org.apache.shiro.authc.AuthenticationToken;

/**
 * Pass-thru token with no credentials checking. Should just be used during startup in trusted
 * environment ie not using any manual login mechanism
 */
public class GlobalInitSysadminAuthenticationToken implements AuthenticationToken {

  /** */
  private static final long serialVersionUID = -1053510239276332847L;

  private String sysadminUsername;

  public GlobalInitSysadminAuthenticationToken() {
    super();
    this.sysadminUsername = Constants.SYSADMIN_UNAME;
  }

  @Override
  public Object getPrincipal() {
    return sysadminUsername;
  }

  @Override
  public Object getCredentials() {
    return sysadminUsername;
  }
}
