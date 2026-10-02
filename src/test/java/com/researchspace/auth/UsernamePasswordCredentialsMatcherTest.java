package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.time.Duration;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
class UsernamePasswordCredentialsMatcherTest {

  private static final RSpacePasswordEncoder ENCODER = new RSpacePasswordEncoder();
  // sysWisc23!, unsalted, as seeded
  private static final String LEGACY =
      ENCODER.wrapLegacySha256(
          "caa6eec0faa20efba3b7af44af7107b05334759954ef3581030cc8e6199a33bf", null);

  private @Mock UserManager userMgr;

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
  void legacyMatchUpgradesStoredHashAndCallerCopy() {
    User user = legacyUser();
    ArgumentCaptor<String> upgraded = ArgumentCaptor.forClass(String.class);
    when(userMgr.upgradePasswordHash(eq("legacy"), eq(LEGACY), upgraded.capture()))
        .thenReturn(true);

    assertTrue(matcher.verifyAndUpgrade(user, "sysWisc23!"));

    assertTrue(upgraded.getValue().startsWith("{" + RSpacePasswordEncoder.ARGON2_ID + "}"));
    assertTrue(ENCODER.matches("sysWisc23!", upgraded.getValue()));
    assertEquals(upgraded.getValue(), user.getPassword());
    assertNull(user.getSalt());
  }

  @Test
  void failedOrSkippedUpgradeStillMatchesAndKeepsCallerCopy() {
    User user = legacyUser();
    when(userMgr.upgradePasswordHash(eq("legacy"), eq(LEGACY), anyString()))
        .thenThrow(new CannotAcquireLockException("lock wait"))
        .thenReturn(false);

    assertTrue(matcher.verifyAndUpgrade(user, "sysWisc23!"));
    assertTrue(matcher.verifyAndUpgrade(user, "sysWisc23!"));
    assertEquals(LEGACY, user.getPassword());
  }

  @Test
  void currentEncodingAndWrongPasswordAreNotRewritten() {
    User user = TestFactory.createAnyUser("current");
    user.setPassword(ENCODER.encode("pw"));

    assertTrue(matcher.verifyAndUpgrade(user, "pw"));
    assertFalse(matcher.verifyAndUpgrade(user, "wrong"));
    user.setPassword(LEGACY);
    assertFalse(matcher.verifyAndUpgrade(user, "wrong"));

    verify(userMgr, never()).upgradePasswordHash(anyString(), anyString(), anyString());
  }

  @Test
  void unrecognisedEncodingDoesNotMatch() {
    User user = TestFactory.createAnyUser("raw");
    user.setPassword("caa6eec0faa20efba3b7af44af7107b05334759954ef3581030cc8e6199a33bf");
    assertFalse(matcher.verifyAndUpgrade(user, "sysWisc23!"));
  }

  @Test
  void busyVerificationPropagates() {
    doThrow(new LoginVerificationBusyException("busy"))
        .when(verifier)
        .verify(anyString(), any(), anyString());

    User user = legacyUser();
    assertThrows(
        LoginVerificationBusyException.class, () -> matcher.verifyAndUpgrade(user, "sysWisc23!"));
    SimpleAuthenticationInfo info =
        new SimpleAuthenticationInfo("legacy", LEGACY, ShiroRealm.DEFAULT_USER_PASSWD_REALM);
    assertThrows(
        LoginVerificationBusyException.class,
        () -> matcher.doCredentialsMatch(new UsernamePasswordToken("legacy", "sysWisc23!"), info));
    verify(userMgr, never()).upgradePasswordHash(anyString(), anyString(), anyString());
  }

  @Test
  void shiroLoginPathUpgradesLegacyHash() {
    SimpleAuthenticationInfo info =
        new SimpleAuthenticationInfo("legacy", LEGACY, ShiroRealm.DEFAULT_USER_PASSWD_REALM);
    ArgumentCaptor<String> upgraded = ArgumentCaptor.forClass(String.class);
    when(userMgr.upgradePasswordHash(eq("legacy"), eq(LEGACY), upgraded.capture()))
        .thenReturn(true);

    assertTrue(matcher.doCredentialsMatch(new UsernamePasswordToken("legacy", "sysWisc23!"), info));
    assertFalse(matcher.doCredentialsMatch(new UsernamePasswordToken("legacy", "x"), info));

    assertTrue(ENCODER.matches("sysWisc23!", upgraded.getValue()));
  }

  @Test
  void bcryptIsRefusedForLoginPasswords() {
    User user = TestFactory.createAnyUser("bcrypt");
    user.setPassword("{bcrypt}$2a$10$fqWevKAPMKNsortKy6gS9eZbYfMnuItTnN4KUf2cy0w0dMTcIjzA6");
    assertFalse(matcher.verifyAndUpgrade(user, "verify1234"));
    verify(verifier, never()).verify(anyString(), any(), anyString());
  }
}
