package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.researchspace.webapp.filter.DefaultLockoutPolicy;
import com.researchspace.webapp.filter.IUserAccountLockoutPolicy;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReauthenticatorTest {
  private @Mock UserManager userMgr;
  private @Mock IVerificationPasswordValidator verificationPasswordValidator;
  private @Mock UserLdapRepo userLdapRepo;
  private @Mock UsernamePasswordCredentialsMatcher credentialsMatcher;
  private @Spy IUserAccountLockoutPolicy lockoutPolicy = new DefaultLockoutPolicy();

  private ShiroTestUtils shiroUtils;
  private @Mock Subject subject;

  User user = null;
  User sysadmin = null;

  @InjectMocks ReauthenticatorImpl reauthenticator;

  @BeforeEach
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Role.SYSTEM_ROLE.getName());
    lenient().when(userMgr.getUserByUsername(user.getUsername(), true)).thenReturn(user);
    lenient().when(userMgr.getUserByUsername(sysadmin.getUsername(), true)).thenReturn(sysadmin);
  }

  @Test
  void fourWrongPasswordsLockTheAccountAndTheFifthSkipsTheCheck() {
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);
    when(credentialsMatcher.test(user, "wrong")).thenReturn(false);

    for (int i = 0; i < 4; i++) {
      assertFalse(reauthenticator.reauthenticate(user, "wrong"));
    }
    assertTrue(user.isAccountLocked());

    assertFalse(reauthenticator.reauthenticate(user, "right"));
    verify(credentialsMatcher, never()).test(user, "right");
  }

  @Test
  void correctPasswordResetsFailureCount() {
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);
    when(credentialsMatcher.test(user, "wrong")).thenReturn(false);
    when(credentialsMatcher.test(user, "right")).thenReturn(true);

    assertFalse(reauthenticator.reauthenticate(user, "wrong"));
    assertEquals(1, user.getNumConsecutiveLoginFailures());
    assertTrue(reauthenticator.reauthenticate(user, "right"));
    assertEquals(0, user.getNumConsecutiveLoginFailures());
    verify(userMgr, times(2)).save(user);
  }

  @Test
  void busyVerificationIsRefusedWithoutCountingAFailure() {
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);
    when(credentialsMatcher.test(user, "any"))
        .thenThrow(new LoginVerificationBusyException("busy"));

    assertFalse(reauthenticator.reauthenticate(user, "any"));
    assertEquals(0, user.getNumConsecutiveLoginFailures());
    verify(userMgr, never()).save(any(User.class));
  }

  @Test
  void operateAsFailuresCountAgainstTheSysadmin() {
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(sysadmin);
    when(credentialsMatcher.test(sysadmin, "wrong")).thenReturn(false);

    assertFalse(reauthenticator.reauthenticate(user, "wrong"));
    assertEquals(1, sysadmin.getNumConsecutiveLoginFailures());
    assertEquals(0, user.getNumConsecutiveLoginFailures());
    verify(credentialsMatcher, never()).test(user, "wrong");
  }

  @Test
  public void testReauthenticateDelegatesToVerificationPasswordIfNeeded() {
    final String ANY_PWD = "anypassword";
    when(verificationPasswordValidator.isVerificationPasswordRequired(user)).thenReturn(true);
    when(verificationPasswordValidator.isVerificationPasswordSet(user)).thenReturn(true);
    when(verificationPasswordValidator.authenticateVerificationPassword(user, ANY_PWD))
        .thenReturn(true);
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);

    assertTrue(reauthenticator.reauthenticate(user, ANY_PWD));
  }

  @Test
  public void reauthenticateReturnsFalseifVerificationPasswordRequiredButNotSet() {
    final String ANY_PWD = "anypassword";
    when(verificationPasswordValidator.isVerificationPasswordRequired(user)).thenReturn(true);
    when(verificationPasswordValidator.isVerificationPasswordSet(user)).thenReturn(false);
    when(userMgr.getOriginalUserForOperateAs(user)).thenReturn(user);

    assertFalse(reauthenticator.reauthenticate(user, ANY_PWD));
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
      assertTrue(reauthenticator.reauthenticate(user, ANY_PWD));
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
    assertTrue(reauthenticator.reauthenticate(user, ANY_PWD));
  }
}
