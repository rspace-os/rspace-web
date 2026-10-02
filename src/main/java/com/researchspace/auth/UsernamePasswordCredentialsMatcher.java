package com.researchspace.auth;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import java.util.function.BiPredicate;
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
 * outdated encoding is replaced after a successful match.
 */
@Slf4j
@Service
public class UsernamePasswordCredentialsMatcher
    implements BiPredicate<User, String>, CredentialsMatcher {

  private @Autowired BoundedPasswordVerifier verifier;
  private @Autowired UserManager userMgr;

  @Override
  public boolean test(User subject, String suppliedPassword) {
    BoundedPasswordVerifier.Result result =
        verify(subject.getUsername(), suppliedPassword, subject.getPassword());
    if (result.upgradedHash() != null) {
      // keep the caller's copy in step, or a later save would re-hash the old hash
      subject.setPassword(result.upgradedHash());
      subject.setSalt(null);
    }
    return result.matches();
  }

  @Override
  public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
    char[] supplied = ((UsernamePasswordToken) token).getPassword();
    if (supplied == null) {
      return false;
    }
    String username = (String) info.getPrincipals().getPrimaryPrincipal();
    return verify(username, new String(supplied), (String) info.getCredentials()).matches();
  }

  private BoundedPasswordVerifier.Result verify(
      String username, String suppliedPassword, String storedPassword) {
    BoundedPasswordVerifier.Result result;
    try {
      result = verifier.verify(username, suppliedPassword, storedPassword);
    } catch (IllegalArgumentException e) {
      log.error("Stored password of [{}] has an unrecognised encoding", username);
      return new BoundedPasswordVerifier.Result(false, null);
    }
    if (result.upgradedHash() != null) {
      userMgr.upgradePasswordHash(username, result.upgradedHash());
    }
    return result;
  }
}
