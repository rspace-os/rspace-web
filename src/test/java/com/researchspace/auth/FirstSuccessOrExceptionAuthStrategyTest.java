package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.auth.password.LoginVerificationBusyException;
import java.util.List;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
import org.apache.shiro.realm.AuthenticatingRealm;
import org.apache.shiro.realm.Realm;
import org.junit.jupiter.api.Test;

/** The login filter only keeps a busy refusal out of lockout if its type reaches the filter. */
class FirstSuccessOrExceptionAuthStrategyTest {

  private static Realm realm(String name, RuntimeException toThrow) {
    AuthenticatingRealm realm =
        new AuthenticatingRealm() {
          @Override
          protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
            throw toThrow;
          }
        };
    realm.setName(name);
    return realm;
  }

  @Test
  void busyExceptionSurvivesMultiRealmAuthentication() {
    ModularRealmAuthenticator authenticator = new ModularRealmAuthenticator();
    authenticator.setAuthenticationStrategy(new FirstSuccessOrExceptionAuthStrategy());
    authenticator.setRealms(
        List.of(
            realm("password", new LoginVerificationBusyException("busy")),
            realm("other", new AuthenticationException("other"))));

    assertThrows(
        LoginVerificationBusyException.class,
        () -> authenticator.authenticate(new UsernamePasswordToken("user", "pw")));
  }
}
