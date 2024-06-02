package com.researchspace.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.impl.VerificationPasswordValidatorImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
public class IVerificationPasswordValidatorTest {
  public final @Rule MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock IPropertyHolder propertyHolder;

  @InjectMocks private VerificationPasswordValidatorImpl verificationValidator;
  User anyUser;

  @Before
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

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
}
