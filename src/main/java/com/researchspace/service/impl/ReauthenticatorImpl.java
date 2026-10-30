package com.researchspace.service.impl;

import com.researchspace.auth.UsernamePasswordCredentialsMatcher;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.ReauthenticationResult;
import com.researchspace.service.UserManager;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ReauthenticatorImpl implements IReauthenticator {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private @Autowired UserManager userMgr;
  private @Autowired IVerificationPasswordValidator verificationPasswordValidator;
  private @Autowired UserLdapRepo userLdapRepo;
  private @Autowired UsernamePasswordCredentialsMatcher credentialsMatcher;

  /**
   * Reauthenticates a user; or if is sysadmin operating as a user, sysadmin can reauthenticate with
   * his own password. A check the password verifier refuses as busy is reported as {@link
   * ReauthenticationResult#BUSY}, not as a wrong password (RSDEV-894). Nothing is written to the
   * user.
   *
   * @param subject The principal user - i.e., the subject, or whoever sysadmin is operating as.
   * @param pwd the password. This should be the subject's password, or the sysadmin password if
   *     operating as.
   */
  public ReauthenticationResult reauthenticate(User subject, String pwd) {

    // check this first, before doing any password validation:  rspac-2223
    User account = userMgr.getOriginalUserForOperateAs(subject);
    return logged(account, () -> checkPassword(account, pwd));
  }

  @Override
  public ReauthenticationResult reauthenticateWithVerificationPassword(
      User passwordOwner, String pwd) {
    return logged(
        passwordOwner,
        () -> verificationPasswordValidator.authenticateVerificationPassword(passwordOwner, pwd));
  }

  private ReauthenticationResult logged(
      User subject, Supplier<ReauthenticationResult> passwordCheck) {
    ReauthenticationResult result;
    try {
      result = passwordCheck.get();
    } catch (LoginVerificationBusyException e) {
      SECURITY_LOG.warn("Reauthentication refused: {}", e.getMessage());
      return ReauthenticationResult.BUSY;
    }
    if (result == ReauthenticationResult.BUSY) {
      SECURITY_LOG.warn("Reauthentication as [{}] refused as busy", subject.getUsername());
    } else if (result == ReauthenticationResult.WRONG) {
      SECURITY_LOG.warn("Failed reauthentication as [{}]", subject.getUsername());
    }
    return result;
  }

  private ReauthenticationResult checkPassword(User subject, String pwd) {
    // If in single sign-on mode or Community Google, check the user's verification
    // password
    if (verificationPasswordValidator.isVerificationPasswordRequired(subject)) {
      // prevent NPE if is not set
      if (verificationPasswordValidator.isVerificationPasswordSet(subject)) {
        return verificationPasswordValidator.authenticateVerificationPassword(subject, pwd);
      } else {
        return ReauthenticationResult.WRONG;
      }
    }

    // for LDAP users re-authenticate with LDAP
    if (SignupSource.LDAP.equals(subject.getSignupSource())) {
      User foundLdapUser = userLdapRepo.authenticate(subject.getUsername(), pwd);
      return ReauthenticationResult.of(foundLdapUser != null);
    }

    // check provided password against default realm
    return ReauthenticationResult.of(credentialsMatcher.verify(subject, pwd));
  }
}
