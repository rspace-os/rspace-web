package com.researchspace.auth;

import com.researchspace.model.User;
import java.util.function.BiPredicate;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.codec.Base64;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import org.springframework.stereotype.Service;

/**
 * Standard username-password matching procedure that replicate Shro's mechanism for use outside of
 * Shiro login mechanism, e.g. for reauthentication.
 */
@Service
public class UsernamePasswordCredentialsMatcher implements BiPredicate<User, String> {

  @Override
  public boolean test(User subject, String suppliedPassword) {
    HashedCredentialsMatcher mt = new HashedCredentialsMatcher(Sha256Hash.ALGORITHM_NAME);
    UsernamePasswordToken token =
        new UsernamePasswordToken(subject.getUsername(), suppliedPassword);
    SimpleAuthenticationInfo sif =
        new SimpleAuthenticationInfo(
            subject.getUsername(), subject.getPassword(), ShiroRealm.DEFAULT_USER_PASSWD_REALM);
    if (subject.getSalt() != null) {
      sif.setCredentialsSalt(ByteSource.Util.bytes(Base64.decode(subject.getSalt())));
    }
    return mt.doCredentialsMatch(token, sif);
  }
}
