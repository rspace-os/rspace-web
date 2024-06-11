package com.researchspace.auth;

import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.SessionControl;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Realm control */
@Slf4j
public class ShiroRealm extends RSpaceRealm implements SessionControl {

  public static final String DEFAULT_USER_PASSWD_REALM = "DefaultUserPasswordRealm";

  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private @Autowired IPropertyHolder properties;

  private boolean ignoreSession;

  public ShiroRealm() {
    setName(DEFAULT_USER_PASSWD_REALM); // This name must match the name in the User class's
    // getPrincipals() method
    setCredentialsMatcher(new HashedCredentialsMatcher(Sha256Hash.ALGORITHM_NAME));
  }

  public void setIgnoreSession(boolean ignoreSession) {
    this.ignoreSession = ignoreSession;
  }

  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authcToken)
      throws AuthenticationException {

    UsernamePasswordToken token = (UsernamePasswordToken) authcToken;
    String incomingUsername = token.getUsername();

    User user = null;
    String username = userMgr.findUsernameByUsernameOrAlias(incomingUsername);
    boolean isRspaceUser = username != null;

    if (isRspaceUser) {
      log.debug("Username {} exists in DB", username);
      if (ignoreSession) {
        // special case flow
        user = userMgr.getUserByUsernameNoSession(username);
      } else {
        // typical flow
        user = userMgr.getUserByUsername(username, true);
      }
    } else {
      log.debug("Username {} does *not* exist in DB", incomingUsername);
    }
    if (user == null) {
      log.debug("User is null, returning");
      return null; // not found in db or couldn't retrieve
    }
    if (SignupSource.LDAP.equals(user.getSignupSource())) {
      log.debug("Signup source is LDAP, returning null. LDAP user must use LdapRealm");
      return null; // LDAP users must authenticate through LdapRealm
    }

    /*
     * RSPAC-2189: standalone auth may be called as a part of SSO deployment, but it only allows SSO_BACKDOOR users.
     * Throwing exception and writing to security log file, as someone trying to log into SSO account is unexpected.
     */
    if (properties.isSSO() && !SignupSource.SSO_BACKDOOR.equals(user.getSignupSource())) {
      SECURITY_LOG.warn(
          "Rejecting DefaultUserPasswordRealm login attempt to a regular user account [{}] in SSO"
              + " mode.",
          incomingUsername);
      Exception e =
          new IncorrectSignupSourceException("non-internal user login attempt in sso mode");
      throw new AuthenticationException(e.getMessage(), e);
    }

    SimpleAuthenticationInfo sif =
        new SimpleAuthenticationInfo(user.getUsername(), user.getPassword(), getName());
    if (user.getSalt() != null) {
      sif.setCredentialsSalt(ByteSource.Util.bytes(Base64.decode(user.getSalt())));
    }
    log.trace("Returning SimpleAuthenticationInfo: {}", sif);
    return sif;
  }
}
