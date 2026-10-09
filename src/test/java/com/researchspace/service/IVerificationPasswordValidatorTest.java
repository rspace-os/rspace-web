package com.researchspace.service;

import static com.researchspace.testutils.LegacyVerificationPasswordFixture.BCRYPT_HASH;
import static com.researchspace.testutils.LegacyVerificationPasswordFixture.PLAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.researchspace.auth.password.BoundedPasswordVerifier;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.auth.password.RSpacePasswordEncoder;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.impl.VerificationPasswordValidatorImpl;
import com.researchspace.testutils.TestFactory;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IVerificationPasswordValidatorTest {
  @Mock IPropertyHolder propertyHolder;
  @Spy RSpacePasswordEncoder passwordEncoder = new RSpacePasswordEncoder();

  @Spy
  BoundedPasswordVerifier verifier =
      new BoundedPasswordVerifier(passwordEncoder, 8, 100, Duration.ofSeconds(5));

  @InjectMocks private VerificationPasswordValidatorImpl verificationValidator;
  User anyUser;

  @BeforeEach
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
  }

  @Test
  public void testIsVerificationPasswordSetSSO() {
    // when unset true unless regular SSO user
    when(propertyHolder.isSSO()).thenReturn(false, true, true);
    assertTrue(verificationValidator.isVerificationPasswordSet(anyUser));
    assertFalse(verificationValidator.isVerificationPasswordSet(anyUser));
    SignupSource defaultSignupSource = anyUser.getSignupSource();
    anyUser.setSignupSource(SignupSource.SSO_BACKDOOR); // sso backdoor user also true
    assertTrue(verificationValidator.isVerificationPasswordSet(anyUser));

    anyUser.setSignupSource(defaultSignupSource);
    anyUser.setVerificationPassword("");
    assertFalse(verificationValidator.isVerificationPasswordSet(anyUser));
    // set password, always true now
    anyUser.setVerificationPassword("anypwd");
    assertTrue(verificationValidator.isVerificationPasswordSet(anyUser));
    assertTrue(verificationValidator.isVerificationPasswordSet(anyUser));
  }

  @Test
  public void testIsVerificationPasswordSetCommunity3rdPartySignup() {
    when(propertyHolder.isSSO()).thenReturn(false);
    assertTrue(verificationValidator.isVerificationPasswordSet(anyUser));
    anyUser.setSignupSource(SignupSource.GOOGLE);
    assertFalse(verificationValidator.isVerificationPasswordSet(anyUser));
    anyUser.setVerificationPassword("some pw");
    assertTrue(verificationValidator.isVerificationPasswordSet(anyUser));
  }

  @Test
  public void testHashAndCheckPasswordRoundtrip() {
    String plaintextPw = "pass";
    String hashedPw = verificationValidator.hashVerificationPassword(plaintextPw);
    anyUser.setVerificationPassword(hashedPw);
    assertTrue(verificationValidator.authenticateVerificationPassword(anyUser, plaintextPw));
  }

  @Test
  public void newVerificationPasswordIsArgon2() {
    assertTrue(
        verificationValidator.hashVerificationPassword("pass").startsWith("{argon2@rspace_v1}"));
  }

  @Test
  public void prefixedBcryptVerificationPasswordMatchesAndIsNotRewritten() {
    String legacy = "{bcrypt}" + BCRYPT_HASH;
    anyUser.setVerificationPassword(legacy);

    assertFalse(verificationValidator.authenticateVerificationPassword(anyUser, "verify12345"));
    assertTrue(verificationValidator.authenticateVerificationPassword(anyUser, PLAIN));
    assertEquals(legacy, anyUser.getVerificationPassword());
  }

  @Test
  public void bareBcryptOrMissingVerificationPasswordDoesNotMatch() {
    anyUser.setVerificationPassword(BCRYPT_HASH);
    assertFalse(verificationValidator.authenticateVerificationPassword(anyUser, PLAIN));
    anyUser.setVerificationPassword(null);
    assertFalse(verificationValidator.authenticateVerificationPassword(anyUser, PLAIN));
  }

  @Test
  public void busyVerificationPropagates() {
    anyUser.setVerificationPassword("{bcrypt}" + BCRYPT_HASH);
    doThrow(new LoginVerificationBusyException("busy"))
        .when(verifier)
        .verify(anyString(), any(), anyString());

    assertThrows(
        LoginVerificationBusyException.class,
        () -> verificationValidator.authenticateVerificationPassword(anyUser, PLAIN));
  }
}
