package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.impl.VerificationPasswordValidatorImpl;
import com.researchspace.testutils.TestFactory;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

@ExtendWith(MockitoExtension.class)
public class IVerificationPasswordValidatorTest {
  @Mock IPropertyHolder propertyHolder;
  @Mock UserManager userMgr;
  @Spy RSpacePasswordEncoder passwordEncoder = new RSpacePasswordEncoder();

  @Spy
  BoundedPasswordVerifier verifier =
      new BoundedPasswordVerifier(passwordEncoder, 8, Duration.ofSeconds(5));

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

  // BCrypt.hashpw("verify1234", BCrypt.gensalt()), as stored before RSDEV-894
  private static final String LEGACY_BCRYPT =
      "$2a$10$fqWevKAPMKNsortKy6gS9eZbYfMnuItTnN4KUf2cy0w0dMTcIjzA6";

  @Test
  public void newVerificationPasswordIsArgon2() {
    assertTrue(
        verificationValidator.hashVerificationPassword("pass").startsWith("{argon2@rspace_v1}"));
  }

  @Test
  public void prefixedBcryptVerificationPasswordMatchesAndIsUpgraded() {
    String legacy = "{bcrypt}" + LEGACY_BCRYPT;
    anyUser.setVerificationPassword(legacy);
    ArgumentCaptor<String> upgraded = ArgumentCaptor.forClass(String.class);
    when(userMgr.upgradeVerificationPasswordHash(
            eq(anyUser.getUsername()), eq(legacy), upgraded.capture()))
        .thenReturn(true);

    assertFalse(verificationValidator.authenticateVerificationPassword(anyUser, "verify12345"));
    assertTrue(verificationValidator.authenticateVerificationPassword(anyUser, "verify1234"));

    assertTrue(upgraded.getValue().startsWith("{argon2@rspace_v1}"));
    assertEquals(upgraded.getValue(), anyUser.getVerificationPassword());
    assertTrue(verificationValidator.authenticateVerificationPassword(anyUser, "verify1234"));
    verify(userMgr).upgradeVerificationPasswordHash(anyString(), anyString(), anyString());
  }

  @Test
  public void bareBcryptOrMissingVerificationPasswordDoesNotMatch() {
    anyUser.setVerificationPassword(LEGACY_BCRYPT);
    assertFalse(verificationValidator.authenticateVerificationPassword(anyUser, "verify1234"));
    anyUser.setVerificationPassword(null);
    assertFalse(verificationValidator.authenticateVerificationPassword(anyUser, "verify1234"));
    verify(userMgr, never()).upgradeVerificationPasswordHash(anyString(), anyString(), anyString());
  }

  @Test
  public void failedOrSkippedUpgradeStillMatchesAndKeepsCallerCopy() {
    String legacy = "{bcrypt}" + LEGACY_BCRYPT;
    anyUser.setVerificationPassword(legacy);
    when(userMgr.upgradeVerificationPasswordHash(
            eq(anyUser.getUsername()), eq(legacy), anyString()))
        .thenThrow(new CannotAcquireLockException("lock wait"))
        .thenReturn(false);

    assertTrue(verificationValidator.authenticateVerificationPassword(anyUser, "verify1234"));
    assertTrue(verificationValidator.authenticateVerificationPassword(anyUser, "verify1234"));
    assertEquals(legacy, anyUser.getVerificationPassword());
  }

  @Test
  public void busyVerificationPropagates() {
    anyUser.setVerificationPassword("{bcrypt}" + LEGACY_BCRYPT);
    doThrow(new LoginVerificationBusyException("busy"))
        .when(verifier)
        .verify(anyString(), any(), anyString());

    assertThrows(
        LoginVerificationBusyException.class,
        () -> verificationValidator.authenticateVerificationPassword(anyUser, "verify1234"));
    verify(userMgr, never()).upgradeVerificationPasswordHash(anyString(), anyString(), anyString());
  }
}
