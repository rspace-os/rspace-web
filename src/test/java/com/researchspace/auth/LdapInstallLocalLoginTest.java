package com.researchspace.auth;

import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.auth.password.SentinelPasswordCheck;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import java.util.List;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.pam.ModularRealmAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** A local (non-directory) account logging in on an install with directory login enabled. */
@ExtendWith(MockitoExtension.class)
class LdapInstallLocalLoginTest {

  private @Mock UserManager passwordRealmUsers;
  private @Mock UserManager ldapRealmUsers;
  private @Mock IPropertyHolder properties;
  private @Mock UsernamePasswordCredentialsMatcher credentialsMatcher;
  private @Mock SentinelPasswordCheck sentinelCheck;
  private @Mock UserLdapRepo userLdapRepo;

  private final ModularRealmAuthenticator authenticator = new ModularRealmAuthenticator();
  private final User alice = createAnyUser("alice");
  private final UsernamePasswordToken token = new UsernamePasswordToken("alice", "password");

  @BeforeEach
  void setUp() {
    ShiroRealm passwordRealm = new ShiroRealm();
    ReflectionTestUtils.setField(passwordRealm, "userMgr", passwordRealmUsers);
    ReflectionTestUtils.setField(passwordRealm, "properties", properties);
    ReflectionTestUtils.setField(passwordRealm, "credentialsMatcher", credentialsMatcher);
    ReflectionTestUtils.setField(passwordRealm, "sentinelCheck", sentinelCheck);
    LdapRealm ldapRealm = new LdapRealm();
    ReflectionTestUtils.setField(ldapRealm, "userManager", ldapRealmUsers);
    ReflectionTestUtils.setField(ldapRealm, "userLdapRepo", userLdapRepo);
    ReflectionTestUtils.setField(ldapRealm, "properties", properties);
    // same realm order and strategy as SecurityRunProdConfig
    authenticator.setRealms(List.of(passwordRealm, ldapRealm));
    authenticator.setAuthenticationStrategy(new FirstSuccessOrExceptionAuthStrategy());

    when(passwordRealmUsers.findUsernameByUsernameOrAlias("alice")).thenReturn("alice");
    when(passwordRealmUsers.getUserByUsername("alice", true)).thenReturn(alice);
  }

  @Test
  void correctPasswordRunsOnePasswordCheck() {
    when(credentialsMatcher.doCredentialsMatch(any(), any())).thenReturn(true);
    when(ldapRealmUsers.userExists("alice")).thenReturn(true);
    when(ldapRealmUsers.getUserByUsername("alice")).thenReturn(alice);

    assertEquals("alice", authenticator.authenticate(token).getPrincipals().getPrimaryPrincipal());
    verify(credentialsMatcher, times(1)).doCredentialsMatch(any(), any());
    verifyNoInteractions(sentinelCheck, userLdapRepo);
  }

  @Test
  void wrongPasswordStopsBeforeTheDirectoryRealm() {
    when(credentialsMatcher.doCredentialsMatch(any(), any())).thenReturn(false);

    assertThrows(IncorrectCredentialsException.class, () -> authenticator.authenticate(token));
    verifyNoInteractions(ldapRealmUsers, userLdapRepo, sentinelCheck);
  }
}
