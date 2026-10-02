package com.researchspace.auth;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Checks a login password against the stored hash for both Shiro login ({@link ShiroRealm}) and
 * reauthentication, through the shared {@link BoundedPasswordVerifier}. A hash stored in an
 * outdated encoding is replaced after a successful match; failing to store it never fails the
 * check.
 */
@Slf4j
@Service
public class UsernamePasswordCredentialsMatcher implements CredentialsMatcher {

  private static final String BCRYPT_PREFIX = "{" + RSpacePasswordEncoder.BCRYPT_ID + "}";

  private @Autowired BoundedPasswordVerifier verifier;
  private @Autowired UserManager userMgr;

  /**
   * Checks the password for reauthentication. If the stored hash is upgraded, the caller's {@code
   * subject} is updated to match, so a later save of it does not treat the old hash as a new
   * password.
   */
  public boolean verifyAndUpgrade(User subject, String suppliedPassword) {
    return verify(subject.getUsername(), suppliedPassword, subject.getPassword(), subject);
  }

  @Override
  public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
    char[] supplied = ((UsernamePasswordToken) token).getPassword();
    if (supplied == null) {
      return false;
    }
    String username = (String) info.getPrincipals().getPrimaryPrincipal();
    return verify(username, new String(supplied), (String) info.getCredentials(), null);
  }

  private boolean verify(
      String username, String suppliedPassword, String storedPassword, User callerCopy) {
    if (storedPassword != null && storedPassword.startsWith(BCRYPT_PREFIX)) {
      log.error("Login password of [{}] has the verification-password-only bcrypt id", username);
      return false;
    }
    BoundedPasswordVerifier.Result result;
    try {
      result = verifier.verify(username, suppliedPassword, storedPassword);
    } catch (IllegalArgumentException e) {
      log.error("Stored password of [{}] cannot be verified", username, e);
      return false;
    }
    if (result.upgradedHash() != null) {
      storeUpgrade(username, storedPassword, result.upgradedHash(), callerCopy);
    }
    return result.matches();
  }

  private void storeUpgrade(String username, String oldHash, String newHash, User callerCopy) {
    try {
      if (!userMgr.upgradePasswordHash(username, oldHash, newHash)) {
        log.info("Password of [{}] changed during verification, upgrade skipped", username);
        return;
      }
    } catch (RuntimeException e) {
      log.warn("Could not store upgraded password hash of [{}], old hash kept", username, e);
      return;
    }
    if (callerCopy != null) {
      callerCopy.setPassword(newHash);
      callerCopy.setSalt(null);
    }
  }
}
