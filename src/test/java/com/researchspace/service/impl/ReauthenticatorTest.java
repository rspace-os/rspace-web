package com.researchspace.service.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.Role;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.IVerificationPasswordValidator;
import com.researchspace.service.UserManager;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ReauthenticatorTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  private @Mock UserManager userMgr;
  private @Mock IVerificationPasswordValidator verificationPasswordValidator;
  private @Mock UserLdapRepo userLdapRepo;

  private ShiroTestUtils shiroUtils;
  private @Mock Subject subject;

  User user = null;
  User sysadmin = null;

  @InjectMocks ReauthenticatorImpl reauthenticator;

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    sysadmin = TestFactory.createAnyUserWithRole("sysadmin", Role.SYSTEM_ROLE.getName());
  }

  @After
  public void tearDown() throws Exception {}

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
