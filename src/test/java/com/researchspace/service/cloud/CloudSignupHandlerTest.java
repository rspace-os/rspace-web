package com.researchspace.service.cloud;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import com.researchspace.service.cloud.impl.CommunityManualUserSignupPolicy;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;

public class CloudSignupHandlerTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock UserManager mgr;
  @Mock CommunityUserManager cloudUserMgr;
  @Mock AnalyticsManager analyticsMgr;

  private IMutablePropertyHolder props;
  private CommunityManualUserSignupPolicy cloudSignupPolicy;

  private MockHttpServletRequest request;

  @Before
  public void setUp() {
    cloudSignupPolicy = new CommunityManualUserSignupPolicy();
    props = new PropertyHolder();
    cloudSignupPolicy.setProperties(props);
    cloudSignupPolicy.setUserManager(mgr);
    cloudSignupPolicy.setCloudUserManager(cloudUserMgr);
    cloudSignupPolicy.setAnalyticsManager(analyticsMgr);

    request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.5");
  }

  @Test(expected = IllegalStateException.class)
  public void testSaveUserThrowsISEIfNotConfiguredForCloud() throws UserExistsException {
    props.setCloud("false");
    cloudSignupPolicy.saveUser(TestFactory.createAnyUser("any"), request);
  }

  @Test()
  public void testSaveInvitedUserHappyCase() throws UserExistsException {
    final User user = TestFactory.createAnyUser("any");
    final TokenBasedVerification token = createAToken(user);
    user.setToken(token.getToken());
    props.setCloud("true");
    when(cloudUserMgr.checkTempCloudUser(user.getEmail())).thenReturn(true);
    when(mgr.userExists(user.getUsername())).thenReturn(false);
    saveUser(user);
    when(mgr.getUserVerificationToken(token.getToken())).thenReturn(token);
    when(cloudUserMgr.checkTempCloudUser(user.getEmail())).thenReturn(true);
    when(cloudUserMgr.activateUser(Mockito.anyString())).thenReturn(user);
    // case where user is already existing as temp user ( perhaps via an invitation)

    User saved = cloudSignupPolicy.saveUser(user, request);
    assertNotNull(saved.getToken());
    verify(cloudUserMgr, times(1)).activateUser(Mockito.anyString());
    verify(analyticsMgr, times(1)).userSignedUp(user, true, request);
  }

  @Test
  public void testSaveInvitedUserWithoutToken() throws UserExistsException {
    final User user = TestFactory.createAnyUser("any");
    props.setCloud("true");
    // case where user is already existing as temp user ( perhaps via an invitation)
    when(cloudUserMgr.checkTempCloudUser(user.getEmail())).thenReturn(true);
    when(mgr.userExists(user.getUsername())).thenReturn(false);
    saveUser(user);

    User saved = cloudSignupPolicy.saveUser(user, request);
    assertNull(saved.getToken());
    verify(cloudUserMgr, never()).activateUser(Mockito.anyString());
  }

  @Test
  public void testSaveInvitedUserIfTokenNotFound() throws UserExistsException {
    final User user = TestFactory.createAnyUser("any");
    final TokenBasedVerification token = createAToken(user);
    user.setToken(token.getToken());
    props.setCloud("true");
    // case where user is already existing as temp user ( perhaps via an invitation)
    when(mgr.getUserVerificationToken(token.getToken())).thenReturn(null);

    when(cloudUserMgr.checkTempCloudUser(user.getEmail())).thenReturn(true);
    handleUserOperations(user);

    User saved = cloudSignupPolicy.saveUser(user, request);
    assertNull(saved.getToken());
    verify(cloudUserMgr, never()).activateUser(Mockito.anyString());
  }

  private void handleUserOperations(final User user) {
    when(mgr.userExists(user.getUsername())).thenReturn(false);
    saveUser(user);
  }

  private void saveUser(final User user) {
    when(mgr.save(user)).thenReturn(user);
    when(mgr.saveUser(user)).thenReturn(user);
    when(cloudUserMgr.mergeSignupFormWithTempUser(user, user.getEmail())).thenReturn(user);
  }

  @Test
  public void testSaveInvitedUserIfTokenInvalid() throws UserExistsException {
    final User user = TestFactory.createAnyUser("any");
    final TokenBasedVerification invalidtoken = createAnInvalidToken(user);
    user.setToken(invalidtoken.getToken());
    props.setCloud("true");
    when(mgr.getUserVerificationToken(invalidtoken.getToken())).thenReturn(invalidtoken);
    when(cloudUserMgr.checkTempCloudUser(user.getEmail())).thenReturn(true);
    handleUserOperations(user);
    User saved = cloudSignupPolicy.saveUser(user, request);
    assertNull(saved.getToken());
    verify(cloudUserMgr, never()).activateUser(Mockito.anyString());
  }

  @Test
  public void registeringSameTemporaryUserAgain() throws UserExistsException {
    final User user = TestFactory.createAnyUser("any");
    final TokenBasedVerification token = createAToken(user);
    user.setToken(token.getToken());
    props.setCloud("true");
    when(mgr.getUserVerificationToken(token.getToken())).thenReturn(null);
    when(cloudUserMgr.checkTempCloudUser(user.getEmail())).thenReturn(true);
    when(mgr.userExists(user.getUsername())).thenReturn(true);
    when(mgr.getUserByUsername(user.getUsername())).thenReturn(user);

    saveUser(user);

    User saved = cloudSignupPolicy.saveUser(user, request);
    assertNull(saved.getToken());
    verify(cloudUserMgr, never()).activateUser(Mockito.anyString());
  }

  @Test(expected = UserExistsException.class)
  public void attemptToSaveInvitedUserWithDuplicatedNameThrowsUEE() throws UserExistsException {
    final User preexistingUser = TestFactory.createAnyUser("any");
    preexistingUser.setEmail("preexistinguser@test.com");
    final User user = TestFactory.createAnyUser("any");
    final TokenBasedVerification token = createAToken(user);
    user.setToken(token.getToken());
    props.setCloud("true");
    // case where user is already existing as temp user ( perhaps via an invitation)
    when(cloudUserMgr.checkTempCloudUser(user.getEmail())).thenReturn(true);
    when(mgr.userExists(user.getUsername())).thenReturn(true);
    when(mgr.getUserByUsername(user.getUsername())).thenReturn(preexistingUser);

    cloudSignupPolicy.saveUser(user, request);

    verify(mgr, never()).saveUser(Mockito.any(User.class));
    verify(cloudUserMgr, never()).activateUser(Mockito.anyString());
  }

  private TokenBasedVerification createAnInvalidToken(User user) {
    return new TokenBasedVerification(
        user.getEmail(), null, TokenBasedVerificationType.PASSWORD_CHANGE);
  }

  private TokenBasedVerification createAToken(User user) {
    return new TokenBasedVerification(
        user.getEmail(), null, TokenBasedVerificationType.VERIFIED_SIGNUP);
  }

  @Test
  public void testSaveNewUnknownUserAsTempUser() throws UserExistsException {
    final User user = TestFactory.createAnyUser("any");

    props.setCloud("true");
    when(cloudUserMgr.checkTempCloudUser(user.getEmail())).thenReturn(false);
    when(mgr.saveNewUser(user)).thenReturn(user);

    User saved = cloudSignupPolicy.saveUser(user, request);
    assertTrue(saved.isTempAccount());
    assertFalse(saved.isEnabled());
    assertNull(saved.getToken());
    verify(cloudUserMgr, never()).mergeSignupFormWithTempUser(user, user.getEmail());
    verify(analyticsMgr, never()).userSignedUp(user, false, request);
  }
}
