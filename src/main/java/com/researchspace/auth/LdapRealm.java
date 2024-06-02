package com.researchspace.auth;

import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import com.researchspace.service.UserSignupException;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class LdapRealm extends RSpaceRealm {

  public static final String LDAP_REALM_NAME = "LDAP_REALM";

  private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);

  private @Autowired UserLdapRepo userLdapRepo;
  private @Autowired UserManager userManager;

  private @Autowired IPropertyHolder properties;

  public LdapRealm() {
    setCredentialsMatcher(new AllowAllCredentialsMatcher());
    setName(LDAP_REALM_NAME);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {

    UsernamePasswordToken upToken = (UsernamePasswordToken) token;
    String username = upToken.getUsername();
    User rspaceUser = null;

    // if user found in RSpace proceed with LDAP authentication only if it's LDAP user
    boolean rspaceUserExists = userManager.userExists(username);
    if (rspaceUserExists) {
      rspaceUser = userManager.getUserByUsername(username);
      if (!SignupSource.LDAP.equals(rspaceUser.getSignupSource())) {
        return null; // not LDAP user, don't try LDAP authentication
      }
    }

    // check provided username/password in LDAP
    log.info(
        "starting LdapRealm authentication for user: {} (exists: {})", username, rspaceUserExists);
    User ldapUser = userLdapRepo.authenticate(username, new String(upToken.getPassword()));

    // incorrect LDAP credentials
    if (ldapUser == null) {
      log.info("incorrect LDAP credentials (or couldn't authenticate)");
      return null;
    }

    // SID verification (RSPAC-1953)
    if (rspaceUserExists && properties.isLdapSidVerificationEnabled()) {
      String savedSid = rspaceUser.getSid();
      String ldapSid = ldapUser.getSid();
      if (StringUtils.isNotBlank(savedSid) && !savedSid.equals(ldapSid)) {
        log.warn(
            "SID retrieved from LDAP ({}) not matching SID saved in database ({}) for username {}. "
                + "Possibly user currently using this username is not the same as user who created "
                + "original RSpace account?",
            ldapSid,
            savedSid,
            username);

        SidVerificationException sve =
            new SidVerificationException(
                "SID values are not matching. That may be caused by "
                    + "username conflict, please contact your System Admin.");
        throw new AuthenticationException(sve.getMessage(), sve);
      }
      ;
    }

    log.info("LDAP credentials correct");

    // if LDAP credentials are correct and user is not in RSpace, try creating an account
    // - if user signup is allowed
    if (!rspaceUserExists && properties.isUserSignup()) {
      try {
        log.info("User not in RSpace yet - proceeding with auto-signup.");
        ldapUser = userLdapRepo.signupLdapUser(ldapUser);
      } catch (UserSignupException e) {
        log.info("Couldn't auto-signup user: " + username, e);
        throw new AuthenticationException(e.getMessage(), e);
      }
    } else if (!rspaceUserExists) {
      // auth filters should prevent this code being reached - see `onAccessDenied` in
      // StandaloneShiroFormAuthFilterExt
      // *If* this code returns null, user sees a 'you have entered an invalid username and
      // password' message
      return null;
    }

    return new SimpleAuthenticationInfo(ldapUser.getUsername(), null, getName());
  }

  /*
   * ==============
   *  for testing
   * ==============
   */
  protected void setUserLdapRepo(UserLdapRepo userLdapRepo) {
    this.userLdapRepo = userLdapRepo;
  }

  protected void setUserManager(UserManager userManager) {
    this.userManager = userManager;
  }

  protected void setProperties(IPropertyHolder properties) {
    this.properties = properties;
  }
}
