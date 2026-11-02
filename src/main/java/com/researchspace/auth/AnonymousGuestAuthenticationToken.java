package com.researchspace.auth;

import com.researchspace.model.RecordGroupSharing;
import org.apache.shiro.authc.AuthenticationToken;

/**
 * Signs a cookieless visitor of a published document or notebook in as the anonymous guest. It
 * carries no credentials and is only constructed by the server, so no request can present it.
 */
public class AnonymousGuestAuthenticationToken implements AuthenticationToken {

  private static final long serialVersionUID = 4529061427015314863L;

  @Override
  public Object getPrincipal() {
    return RecordGroupSharing.ANONYMOUS_USER;
  }

  @Override
  public Object getCredentials() {
    return null;
  }
}
