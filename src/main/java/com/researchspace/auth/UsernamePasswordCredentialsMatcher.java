package com.researchspace.auth;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.auth.password.SentinelPasswordCheck;
import com.researchspace.model.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Checks a login password against the stored hash for both Shiro login ({@link ShiroRealm}) and
 * reauthentication, through the shared {@link BoundedPasswordVerifier}. A check never writes: a
 * hash stored in the legacy encoding stays as it is (ADR 0011). The {@code {bcrypt}} id the shared
 * encoder registers for verification passwords is refused here, so a verification password can
 * never be used to log in.
 */
@Slf4j
@Service
public class UsernamePasswordCredentialsMatcher implements CredentialsMatcher {

  private static final String BCRYPT_PREFIX = "{" + RSpacePasswordEncoder.BCRYPT_ID + "}";

  private @Autowired BoundedPasswordVerifier verifier;
  private @Autowired SentinelPasswordCheck sentinelCheck;

  /** Checks the password for reauthentication. */
  public boolean verify(User subject, String suppliedPassword) {
    return verify(subject.getUsername(), suppliedPassword, subject.getPassword());
  }

  @Override
  public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
    char[] supplied = ((UsernamePasswordToken) token).getPassword();
    if (supplied == null) {
      return false;
    }
    String username = (String) info.getPrincipals().getPrimaryPrincipal();
    return verify(username, new String(supplied), (String) info.getCredentials());
  }

  private boolean verify(String username, String suppliedPassword, String storedPassword) {
    if (storedPassword != null && storedPassword.startsWith(BCRYPT_PREFIX)) {
      log.error("Login password of [{}] has the verification-password-only bcrypt id", username);
      return false;
    }
    try {
      return verifier.verify(username, suppliedPassword, storedPassword);
    } catch (IllegalArgumentException e) {
      sentinelCheck.pad(username, suppliedPassword);
      log.warn(
          "Stored password of [{}] has no recognised encoding; an administrator must reset it",
          username);
      return false;
    }
  }
}
