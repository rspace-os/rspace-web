package com.researchspace.service.impl;

import static com.researchspace.service.ReauthenticationResult.BUSY;
import static com.researchspace.service.ReauthenticationResult.OK;
import static com.researchspace.service.ReauthenticationResult.WRONG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.UsernamePasswordCredentialsMatcher;
import com.researchspace.auth.password.LoginVerificationBusyException;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.Role;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.TestFactory;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReauthenticatorTest {
  private @Mock UserManager userMgr;
  private @Mock IVerificationPasswordValidator verificationPasswordValidator;
  private @Mock UserLdapRepo userLdapRepo;
  private @Mock UsernamePasswordCredentialsMatcher credentialsMatcher;

  private ShiroTestUtils shiroUtils;
  private @Mock Subject subject;

  User user = null;
  User sysadmin = null;

  @InjectMocks ReauthenticatorImpl reauthenticator;

  @BeforeEach
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Role.SYSTEM_ROLE.getName());
  }

  @Test
  void busyVerificationIsReportedAsBusyNotWrong() {
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);
    when(credentialsMatcher.verify(user, "any"))
        .thenThrow(new LoginVerificationBusyException("busy"));

    assertEquals(BUSY, reauthenticator.reauthenticate(user, "any"));
  }

  @Test
  void failedPasswordCheckWritesNothing() {
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);
    when(credentialsMatcher.verify(user, "wrong")).thenReturn(false);

    assertEquals(WRONG, reauthenticator.reauthenticate(user, "wrong"));
    assertEquals(0, user.getNumConsecutiveLoginFailures());
    verify(userMgr, never()).save(any(User.class));
  }

  @Test
  void ownVerificationPasswordCheckSkipsOperateAsSubstitution() {
    when(verificationPasswordValidator.authenticateVerificationPassword(user, "wrong"))
        .thenReturn(false);
    when(verificationPasswordValidator.authenticateVerificationPassword(user, "right"))
        .thenReturn(true);
    when(verificationPasswordValidator.authenticateVerificationPassword(user, "busy"))
        .thenThrow(new LoginVerificationBusyException("busy"));

    assertEquals(BUSY, reauthenticator.reauthenticateWithVerificationPassword(user, "busy"));
    assertEquals(WRONG, reauthenticator.reauthenticateWithVerificationPassword(user, "wrong"));
    assertEquals(OK, reauthenticator.reauthenticateWithVerificationPassword(user, "right"));
    verify(userMgr, never()).getOriginalUserForOperateAs(user);
    verify(userMgr, never()).save(any(User.class));
  }

  @Test
  void operateAsChecksTheSysadminPassword() {
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(sysadmin);
    when(credentialsMatcher.verify(sysadmin, "wrong")).thenReturn(false);

    assertEquals(WRONG, reauthenticator.reauthenticate(user, "wrong"));
    verify(credentialsMatcher, never()).verify(user, "wrong");
  }

  @Test
  public void testReauthenticateDelegatesToVerificationPasswordIfNeeded() {
    final String ANY_PWD = "anypassword";
    when(verificationPasswordValidator.isVerificationPasswordRequired(user)).thenReturn(true);
    when(verificationPasswordValidator.isVerificationPasswordSet(user)).thenReturn(true);
    when(verificationPasswordValidator.authenticateVerificationPassword(user, ANY_PWD))
        .thenReturn(true);
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);

    assertEquals(OK, reauthenticator.reauthenticate(user, ANY_PWD));
  }

  @Test
  public void reauthenticateReturnsFalseifVerificationPasswordRequiredButNotSet() {
    final String ANY_PWD = "anypassword";
    when(verificationPasswordValidator.isVerificationPasswordRequired(user)).thenReturn(true);
    when(verificationPasswordValidator.isVerificationPasswordSet(user)).thenReturn(false);
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);

    assertEquals(WRONG, reauthenticator.reauthenticate(user, ANY_PWD));
  }

  @Test
  public void testReauthenticateDelegatesToLdapIfNeeded() {
    user.setSignupSource(SignupSource.LDAP);
    shiroUtils = new ShiroTestUtils();
    shiroUtils.setSubject(subject);

    try {
      final String ANY_PWD = "anypassword";
      when(userLdapRepo.authenticate(user.getUsername(), ANY_PWD)).thenReturn(user);
      when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);
      assertEquals(OK, reauthenticator.reauthenticate(user, ANY_PWD));
    } finally {
      shiroUtils.clearSubject();
    }
  }

  @Test
  public void sysadminVPIsCheckedWhenOperateAs() {
    final String ANY_PWD = "anypassword";
    // assert that authentication is based on sysadmin, not user
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(sysadmin);
    when(verificationPasswordValidator.isVerificationPasswordRequired(sysadmin)).thenReturn(true);
    when(verificationPasswordValidator.isVerificationPasswordSet(sysadmin)).thenReturn(true);
    when(verificationPasswordValidator.authenticateVerificationPassword(sysadmin, ANY_PWD))
        .thenReturn(true);
    assertEquals(OK, reauthenticator.reauthenticate(user, ANY_PWD));
  }
}
