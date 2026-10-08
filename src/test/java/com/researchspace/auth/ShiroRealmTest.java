package com.researchspace.auth;

import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.password.SentinelPasswordCheck;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShiroRealmTest {

  private @Mock UserManager userMgr;
  private @Mock IPropertyHolder properties;
  private @Mock SentinelPasswordCheck sentinelCheck;
  private @InjectMocks ShiroRealm realm;

  @Test
  void unknownUsernameIsPaddedThroughTheVerifier() {
    when(userMgr.findUsernameByUsernameOrAlias("Nobody")).thenReturn(null);
    when(userMgr.loginLockKey("Nobody")).thenReturn("nobody-key");

    assertNull(realm.doGetAuthenticationInfo(new UsernamePasswordToken("Nobody", "guess")));
    verify(sentinelCheck).pad("nobody-key", "guess");
  }

  @Test
  void ldapSourceUserIsPaddedWhenLdapAuthenticationIsOff() {
    User ldapUser = knownUser("ldapuser");
    ldapUser.setSignupSource(SignupSource.LDAP);
    when(userMgr.loginLockKey("ldapuser")).thenReturn("ldapuser-key");

    assertNull(realm.doGetAuthenticationInfo(new UsernamePasswordToken("ldapuser", "guess")));
    verify(sentinelCheck).pad("ldapuser-key", "guess");
  }

  @Test
  void ldapSourceUserIsNotPaddedWhenTheDirectoryChecksIt() {
    User ldapUser = knownUser("ldapuser");
    ldapUser.setSignupSource(SignupSource.LDAP);
    when(properties.isLdapAuthenticationEnabled()).thenReturn(true);

    assertNull(realm.doGetAuthenticationInfo(new UsernamePasswordToken("ldapuser", "guess")));
    verify(sentinelCheck, never()).pad(any(), any());
  }

  @Test
  void knownInternalUserIsNotPadded() {
    knownUser("internal");

    assertNotNull(realm.doGetAuthenticationInfo(new UsernamePasswordToken("internal", "guess")));
    verify(sentinelCheck, never()).pad(any(), any());
  }

  private User knownUser(String username) {
    User user = createAnyUser(username);
    when(userMgr.findUsernameByUsernameOrAlias(username)).thenReturn(username);
    when(userMgr.getUserByUsername(username, true)).thenReturn(user);
    return user;
  }
}
