package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import java.time.Duration;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

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

  @Test
  void legacyMatchUpgradesStoredHashAndCallerCopy() {
    User user = TestFactory.createAnyUser("legacy");
    user.setPassword(LEGACY);
    user.setSalt("ignored");

    assertTrue(matcher.test(user, "sysWisc23!"));

    assertTrue(user.getPassword().startsWith("{" + RSpacePasswordEncoder.ARGON2_ID + "}"));
    assertNull(user.getSalt());
    verify(userMgr).upgradePasswordHash("legacy", user.getPassword());
  }

  @Test
  void currentEncodingAndWrongPasswordAreNotRewritten() {
    User user = TestFactory.createAnyUser("current");
    String current = ENCODER.encode("pw");
    user.setPassword(current);

    assertTrue(matcher.test(user, "pw"));
    assertFalse(matcher.test(user, "wrong"));
    user.setPassword(LEGACY);
    assertFalse(matcher.test(user, "wrong"));

    verify(userMgr, never()).upgradePasswordHash(anyString(), anyString());
  }

  @Test
  void unrecognisedEncodingDoesNotMatch() {
    User user = TestFactory.createAnyUser("raw");
    user.setPassword("caa6eec0faa20efba3b7af44af7107b05334759954ef3581030cc8e6199a33bf");
    assertFalse(matcher.test(user, "sysWisc23!"));
  }

  @Test
  void shiroLoginPathUpgradesLegacyHash() {
    SimpleAuthenticationInfo info =
        new SimpleAuthenticationInfo("legacy", LEGACY, ShiroRealm.DEFAULT_USER_PASSWD_REALM);
    assertTrue(matcher.doCredentialsMatch(new UsernamePasswordToken("legacy", "sysWisc23!"), info));
    assertFalse(matcher.doCredentialsMatch(new UsernamePasswordToken("legacy", "x"), info));
    verify(userMgr).upgradePasswordHash(anyString(), anyString());
    assertEquals(LEGACY, info.getCredentials());
  }
}
