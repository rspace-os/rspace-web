package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.UserManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
public class VerificationPasswordControllerTest {

  public final @Rule MockitoRule mockitoRule = MockitoJUnit.rule();
  private @Mock UserManager userMgr;
  private @Mock UserValidator userValidator;
  private @Mock IVerificationPasswordValidator verificationPasswordValidator;

  @InjectMocks private VerificationPasswordController verificationPasswordController;
  User anyUser;
  final String OK_PWD = "abcdefg";

  @Before
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

  // this is the only method not very similar t oSignup/Profile tests
  @Test
  public void testSetVerificationPassword() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(verificationPasswordValidator.isVerificationPasswordSet(anyUser)).thenReturn(true);

    verificationPasswordController.setVerificationPassword(OK_PWD, OK_PWD);
    assertUserPasswordNotSaved();

    // only saved if validator is OK

  }

  @Test
  public void testSetVerificationPassword2() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(verificationPasswordValidator.isVerificationPasswordSet(anyUser)).thenReturn(false);
    when(userValidator.validatePasswords(
            OK_PWD, "not matching confirm password", anyUser.getUsername()))
        .thenReturn("some error");
    verificationPasswordController.setVerificationPassword(OK_PWD, "not matching confirm password");
    assertUserPasswordNotSaved();
  }

  @Test
  public void testSetVerificationPasswordHappyCase() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(anyUser);
    when(verificationPasswordValidator.isVerificationPasswordSet(anyUser)).thenReturn(false);
    when(userValidator.validatePasswords(OK_PWD, OK_PWD, anyUser.getUsername()))
        .thenReturn(UserValidator.FIELD_OK);
    when(verificationPasswordValidator.hashVerificationPassword(OK_PWD)).thenReturn("hashedPW");
    verificationPasswordController.setVerificationPassword(OK_PWD, OK_PWD);
    assertUserPwdSaved();
    assertEquals("hashedPW", anyUser.getVerificationPassword());
  }

  private void assertUserPwdSaved() {
    Mockito.verify(userMgr, times(1)).saveUser(anyUser);
  }

  private void assertUserPasswordNotSaved() {
    verify(userMgr, never()).saveUser(anyUser);
  }
}
