package com.researchspace.service.cloud;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.service.cloud.impl.CloudUserManagerImpl;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

@CommunityTestContext
public class CloudUserManagerTest extends SpringTransactionalTest {

  private @Autowired CommunityUserManager cloudUserMgr;
  private @Autowired AnalyticsManager analyticsManager;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    ((CloudUserManagerImpl) cloudUserMgr).setAnalyticsManager(analyticsManager);
  }

  @Test
  public void testActivateUser() {
    String unique = getRandomAlphabeticString("any");
    User tmpuser = cloudUserMgr.createInvitedUser(unique + "@x.com");
    TokenBasedVerification token =
        userMgr.createTokenBasedVerificationRequest(
            tmpuser, tmpuser.getEmail(), "any", TokenBasedVerificationType.VERIFIED_SIGNUP);
    // sanity check
    assertFalse(tmpuser.isEnabled());
    assertTrue(tmpuser.isTempAccount());

    // unknown token handled gracefully
    User activated = cloudUserMgr.activateUser("unknowntoken");
    assertNull(activated);

    // now activate and assert all well
    activated = cloudUserMgr.activateUser(token.getToken());

    assertTrue(activated.isEnabled());
    assertFalse(activated.isTempAccount());
    token = userMgr.getUserVerificationToken(token.getToken());
    assertTrue(token.isResetCompleted());
  }

  @Test
  public void testAnalyticsCalledAfterActivation() {

    AnalyticsManager analyticsManagerMock = mock(AnalyticsManager.class);
    ((CloudUserManagerImpl) cloudUserMgr).setAnalyticsManager(analyticsManagerMock);

    String unique = getRandomAlphabeticString("any");
    User tmpuser = cloudUserMgr.createInvitedUser(unique + "@x.com");
    TokenBasedVerification token =
        userMgr.createTokenBasedVerificationRequest(
            tmpuser, tmpuser.getEmail(), "any", TokenBasedVerificationType.VERIFIED_SIGNUP);

    User activated = cloudUserMgr.activateUser("unknowntoken");
    assertNull(activated);
    verify(analyticsManagerMock, never()).userCreated(Mockito.any(User.class));

    // analytics only called after successful activation
    activated = cloudUserMgr.activateUser(token.getToken());
    assertTrue(activated.isEnabled());
    verify(analyticsManagerMock, times(1)).userCreated(tmpuser);
  }

  @Test
  public void testChangeUsersEmail() {

    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    String updatedEmail = "changedEmail@x.com";
    String remoteAddr = "125_address";
    TokenBasedVerification changeEmailToken =
        cloudUserMgr.emailChangeRequested(user, updatedEmail, remoteAddr);

    // token should be generated
    assertEquals(updatedEmail, changeEmailToken.getEmail());
    assertEquals(user, changeEmailToken.getUser());
    assertEquals(TokenBasedVerificationType.EMAIL_CHANGE, changeEmailToken.getVerificationType());
    assertFalse(changeEmailToken.isResetCompleted());

    boolean changeEmailResult =
        cloudUserMgr.emailChangeConfirmed(changeEmailToken.getToken(), user);
    // token should be marked as used
    assertTrue(changeEmailResult);

    // user details should be updated
    user = userMgr.getUser(user.getId().toString());
    assertEquals(updatedEmail, user.getEmail());
  }

  @Test
  public void createInvitedUserListRemovesDuplicates() {
    assertEquals(1, cloudUserMgr.createInvitedUserList(toList("x@rc.com", "x@rc.com")).size());
  }

  @Test
  public void testUserEmailChangeIgnoredIfEmailAlreadyTaken() {

    User user1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("any1"));
    User user2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("any2"));

    String updatedEmail = "changedEmail@x.com";
    String remoteAddr = "125_address";

    // two users try to change
    TokenBasedVerification changeEmailToken1 =
        cloudUserMgr.emailChangeRequested(user1, updatedEmail, remoteAddr);
    TokenBasedVerification changeEmailToken2 =
        cloudUserMgr.emailChangeRequested(user2, updatedEmail, remoteAddr);

    // user1 tries to use user2's token, should fail
    boolean changeEmailUser1 =
        cloudUserMgr.emailChangeConfirmed(changeEmailToken2.getToken(), user1);
    assertFalse(changeEmailUser1);

    // user1 trying to use own token, should succeed
    changeEmailUser1 = cloudUserMgr.emailChangeConfirmed(changeEmailToken1.getToken(), user1);
    assertTrue(changeEmailUser1);
    user1 = userMgr.getUser(user1.getId().toString());
    assertEquals(updatedEmail, user1.getEmail());

    // user2 tries to use their token, but email is already taken
    boolean changeEmailUser2 =
        cloudUserMgr.emailChangeConfirmed(changeEmailToken2.getToken(), user2);
    assertFalse(changeEmailUser2);
    String user2OrgEmail = user2.getEmail();
    user2 = userMgr.getUser(user2.getId().toString());
    assertEquals(user2OrgEmail, user2.getEmail());
  }
}
