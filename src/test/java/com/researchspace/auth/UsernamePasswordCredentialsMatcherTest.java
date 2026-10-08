package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.User;
import com.researchspace.testutils.TestFactory;
import java.time.Duration;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsernamePasswordCredentialsMatcherTest {

  private static final RSpacePasswordEncoder ENCODER = new RSpacePasswordEncoder();
  // sysWisc23!, unsalted, as seeded
  private static final String LEGACY =
      ENCODER.wrapLegacySha256(
          "caa6eec0faa20efba3b7af44af7107b05334759954ef3581030cc8e6199a33bf", null);

  private @Spy BoundedPasswordVerifier verifier =
      new BoundedPasswordVerifier(ENCODER, 8, Duration.ofSeconds(5));

  private @InjectMocks UsernamePasswordCredentialsMatcher matcher;

  private User legacyUser() {
    User user = TestFactory.createAnyUser("legacy");
    user.setPassword(LEGACY);
    user.setSalt("ignored");
    return user;
  }

  @Test
  void legacyAndCurrentHashesVerifyWithoutBeingRewritten() {
    User user = legacyUser();
    assertTrue(matcher.verify(user, "sysWisc23!"));
    assertFalse(matcher.verify(user, "wrong"));
    assertEquals(LEGACY, user.getPassword());

    String current = ENCODER.encode("pw");
    user.setPassword(current);
    assertTrue(matcher.verify(user, "pw"));
    assertFalse(matcher.verify(user, "wrong"));
    assertEquals(current, user.getPassword());
  }

  @Test
  void nullPasswordDoesNotMatchWithoutACheck() {
    SimpleAuthenticationInfo info =
        new SimpleAuthenticationInfo("legacy", LEGACY, ShiroRealm.DEFAULT_USER_PASSWD_REALM);

    assertFalse(
        matcher.doCredentialsMatch(new UsernamePasswordToken("legacy", (char[]) null), info));
    verifyNoInteractions(verifier);
  }

  @Test
  void unrecognisedEncodingDoesNotMatch() {
    User user = TestFactory.createAnyUser("raw");
    user.setPassword("caa6eec0faa20efba3b7af44af7107b05334759954ef3581030cc8e6199a33bf");
    assertFalse(matcher.verify(user, "sysWisc23!"));
  }

  @Test
  void busyVerificationPropagates() {
    doThrow(new LoginVerificationBusyException("busy"))
        .when(verifier)
        .verify(anyString(), any(), anyString());

    User user = legacyUser();
    assertThrows(LoginVerificationBusyException.class, () -> matcher.verify(user, "sysWisc23!"));
    SimpleAuthenticationInfo info =
        new SimpleAuthenticationInfo("legacy", LEGACY, ShiroRealm.DEFAULT_USER_PASSWD_REALM);
    assertThrows(
        LoginVerificationBusyException.class,
        () -> matcher.doCredentialsMatch(new UsernamePasswordToken("legacy", "sysWisc23!"), info));
  }

  @Test
  void shiroLoginPathVerifiesLegacyHash() {
    SimpleAuthenticationInfo info =
        new SimpleAuthenticationInfo("legacy", LEGACY, ShiroRealm.DEFAULT_USER_PASSWD_REALM);
    assertTrue(matcher.doCredentialsMatch(new UsernamePasswordToken("legacy", "sysWisc23!"), info));
    assertFalse(matcher.doCredentialsMatch(new UsernamePasswordToken("legacy", "x"), info));
    assertEquals(LEGACY, info.getCredentials());
  }

  @Test
  void bcryptIsRefusedForLoginPasswords() {
    User user = TestFactory.createAnyUser("bcrypt");
    user.setPassword("{bcrypt}$2a$10$fqWevKAPMKNsortKy6gS9eZbYfMnuItTnN4KUf2cy0w0dMTcIjzA6");
    assertFalse(matcher.verify(user, "verify1234"));
    verify(verifier, never()).verify(anyString(), any(), anyString());
  }
}
