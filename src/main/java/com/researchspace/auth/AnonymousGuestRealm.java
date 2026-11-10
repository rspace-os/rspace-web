package com.researchspace.auth;

import com.researchspace.model.RecordGroupSharing;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;

/**
 * Signs in the anonymous guest for public links without a password check, so public visitors take
 * no password-verification slot (ADR 0014). Accepts only {@link AnonymousGuestAuthenticationToken}
 * and always signs in {@link RecordGroupSharing#ANONYMOUS_USER}. Disabling the guest account turns
 * public links off.
 */
public class AnonymousGuestRealm extends RSpaceRealm {
  public static final String REALM_NAME = "ANONYMOUS_GUEST";

  public AnonymousGuestRealm() {
    setAuthenticationTokenClass(AnonymousGuestAuthenticationToken.class);
    setCredentialsMatcher(new AllowAllCredentialsMatcher());
    setName(REALM_NAME);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    String guest = RecordGroupSharing.ANONYMOUS_USER;
    if (!userMgr.getUserByUsername(guest).isEnabled()) {
      throw new DisabledAccountException("The anonymous guest account is disabled");
    }
    return new SimpleAuthenticationInfo(guest, null, getName());
  }
}
