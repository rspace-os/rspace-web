package com.researchspace.auth;

import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class LdapRealmTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock private UserManager userManager;
  @Mock private UserLdapRepo userLdapRepo;
  @Mock private IPropertyHolder properties;

  @InjectMocks private LdapRealm ldapRealm;
  private String testUsername;
  private User user1noSid;
  private User user1sid1;
  private User user1sid2;
  private UsernamePasswordToken token;

  @Before
  public void setUp() {
    // let's create 3 user objects representing user1: one without sid, and two with different sids
    testUsername = "user1";
    user1noSid = createAnyUser(testUsername);
    user1noSid.setSignupSource(SignupSource.LDAP);
    user1noSid.setId(1L);
    user1sid1 = createAnyUser(testUsername);
    user1sid1.setSignupSource(SignupSource.LDAP);
    user1sid1.setSid("1-2-3");
    user1sid1.setId(1L);
    user1sid2 = createAnyUser(testUsername);
    user1sid2.setSignupSource(SignupSource.LDAP);
    user1sid2.setSid("1-2-3-4");
    user1sid2.setId(1L);
    token = new UsernamePasswordToken(testUsername, "anypass");
    when(userLdapRepo.authenticate(anyString(), anyString())).thenReturn(user1sid2);
    when(properties.isUserSignup()).thenReturn(false);
  }

  @Test
  public void testSidVerification() throws Exception {

    when(userManager.userExists(any())).thenReturn(true);

    // user should be allowed to login if no sid saved in db
    when(userManager.getUserByUsername(testUsername)).thenReturn(user1noSid);
    ldapRealm.doGetAuthenticationInfo(token);
    // with sid verification disabled (by default) they can login even if sid doesn't match
    when(userManager.getUserByUsername(testUsername)).thenReturn(user1sid1);
    ldapRealm.doGetAuthenticationInfo(token);

    // let's mark sid verification as enabled
    when(properties.isLdapSidVerificationEnabled()).thenReturn(true);

    // user should still be allowed to login if no sid saved in db
    when(userManager.getUserByUsername(testUsername)).thenReturn(user1noSid);
    ldapRealm.doGetAuthenticationInfo(token);
    // ... but with sid verification enabled expect exception if sid doesn't match
    when(userManager.getUserByUsername(testUsername)).thenReturn(user1sid1);
    CoreTestUtils.assertExceptionThrown(
        () -> ldapRealm.doGetAuthenticationInfo(token),
        AuthenticationException.class,
        containsString("SID values are not matching"));
  }

  @Test
  public void testUserNotExistsReturnsNullIfUserSignupNotEnabled() throws Exception {
    when(userManager.userExists(any())).thenReturn(false);
    assertNull(ldapRealm.doGetAuthenticationInfo(token));
  }

  @Test
  public void testUserNotExistsCreatesNewUserIfUserSignupIsEnabled() throws Exception {
    when(userManager.userExists(any())).thenReturn(false);
    when(userLdapRepo.signupLdapUser(eq(user1sid2))).thenReturn(user1sid2);
    when(properties.isUserSignup()).thenReturn(true);
    AuthenticationInfo authenticationInfo = ldapRealm.doGetAuthenticationInfo(token);
    assertEquals("user1", authenticationInfo.getPrincipals().toString());
  }
}
