package com.researchspace.service.impl;

import com.researchspace.auth.UsernamePasswordCredentialsMatcher;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.filter.IUserAccountLockoutPolicy;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ReauthenticatorImpl implements IReauthenticator {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private @Autowired UserManager userMgr;
  private @Autowired IVerificationPasswordValidator verificationPasswordValidator;
  private @Autowired UserLdapRepo userLdapRepo;
  private @Autowired UsernamePasswordCredentialsMatcher credentialsMatcher;
  private @Autowired IUserAccountLockoutPolicy lockoutPolicy;

  /**
   * Reauthenticates a user; or if is sysadmin operating as a user, sysadmin can reauthenticate with
   * his own password. Failures share the failed-login counter, and a locked-out account is refused
   * without checking the password (RSDEV-894).
   *
   * @param subject The principal user - i.e., the subject, or whoever sysadmin is operating as.
   * @param pwd the password. This should be the subject's password, or the sysadmin password if
   *     operating as.
   */
  public boolean reauthenticate(User subject, String pwd) {

    // check this first, before doing any password validation:  rspac-2223
    subject = userMgr.getOriginalUserForOperateAs(subject);
    String caller = callingAction();

    if (lockoutPolicy.isReauthenticationLocked(subject)) {
      SECURITY_LOG.warn(
          "Reauthentication as [{}] by {} refused, the account is temporarily locked",
          subject.getUsername(),
          caller);
      return false;
    }

    boolean authenticated;
    try {
      authenticated = checkPassword(subject, pwd);
    } catch (LoginVerificationBusyException e) {
      SECURITY_LOG.warn("Reauthentication by {}: {}", caller, e.getMessage());
      return false;
    }

    if (authenticated) {
      if (subject.getLoginFailure() != null) {
        lockoutPolicy.handleLockoutOnSuccess(subject);
        userMgr.save(subject);
      }
    } else {
      SECURITY_LOG.warn("Failed reauthentication as [{}] by {}", subject.getUsername(), caller);
      lockoutPolicy.handleReauthenticationFailure(subject);
      userMgr.save(subject);
    }
    return authenticated;
  }

  private boolean checkPassword(User subject, String pwd) {
    // If in single sign-on mode or Community Google, check the user's verification
    // password
    if (verificationPasswordValidator.isVerificationPasswordRequired(subject)) {
      // prevent NPE if is not set
      if (verificationPasswordValidator.isVerificationPasswordSet(subject)) {
        return verificationPasswordValidator.authenticateVerificationPassword(subject, pwd);
      } else {
        return false;
      }
    }

    // for LDAP users re-authenticate with LDAP
    if (SignupSource.LDAP.equals(subject.getSignupSource())) {
      User foundLdapUser = userLdapRepo.authenticate(subject.getUsername(), pwd);
      return foundLdapUser != null;
    }

    // check provided password against default realm
    return credentialsMatcher.verify(subject, pwd);
  }

  private static String callingAction() {
    // aspects such as ServiceLoggerAspct sit between this class and its caller
    return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .walk(
            frames ->
                frames
                    .filter(f -> f.getDeclaringClass() != ReauthenticatorImpl.class)
                    .filter(f -> !f.getDeclaringClass().isAnnotationPresent(Aspect.class))
                    .filter(f -> f.getClassName().startsWith("com.researchspace"))
                    .findFirst()
                    .map(f -> f.getClassName() + "." + f.getMethodName())
                    .orElse("unknown caller"));
  }
}
