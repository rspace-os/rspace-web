package com.researchspace.service.cloud.impl;

import static com.researchspace.core.testutil.MockLoggingUtils.assertNoLogging;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;

import com.axiope.userimport.IPostUserSignup;
import com.researchspace.model.TokenBasedVerification;
import com.researchspace.model.TokenBasedVerificationType;
import com.researchspace.model.User;
import com.researchspace.service.impl.ConfigurableLogger;
import com.researchspace.testutils.CommunityTestContext;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mock.web.MockHttpServletRequest;

@CommunityTestContext
public class CommunityPostSignupVerificationTest extends SpringTransactionalTest {

  MockHttpServletRequest request;
  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock Logger log;

  @Before
  public void setUp() {
    request = new MockHttpServletRequest();
    getBeanOfClass(ConfigurableLogger.class).setLogger(log);
  }

  @After
  public void teardown() {
    getBeanOfClass(ConfigurableLogger.class).setLoggerDefault();
  }

  @Autowired
  @Qualifier("communityPostSignup")
  private IPostUserSignup postSignup;

  @Test
  public void testPostUserCreatedThroughSignupPage() {
    request.setRemoteHost("http://remoteHost.com");
    User tempUser = createAndSaveTmpUserIfNotExists(getRandomAlphabeticString("tempuser"));

    postSignup.postUserCreate(tempUser, request, TESTPASSWD);
    Mockito.verify(log, times(1))
        .info(
            Mockito.anyString(),
            Mockito.anyBoolean(),
            Mockito.contains("Welcome"),
            Mockito.contains(tempUser.getEmail()),
            Mockito.contains("/verifysignup?token="));
    assertTrue(tokenCreated(tempUser));
  }

  @Test
  public void testPostUserCreatedThroughInvitation() {
    request.setRemoteHost("http://remoteHost.com");
    User tempUser = createAndSaveTmpUserIfNotExists(getRandomAlphabeticString("tempuser"));
    TokenBasedVerification token =
        new TokenBasedVerification(
            tempUser.getEmail(), null, TokenBasedVerificationType.VERIFIED_SIGNUP);
    tempUser.setToken(token.getToken());
    // if user has token, they've already been verified by email, so we don't send another
    postSignup.postUserCreate(tempUser, request, TESTPASSWD);
    assertNoLogging(log);
  }

  private boolean tokenCreated(User tempUser) {
    List<TokenBasedVerification> results =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from TokenBasedVerification where email=:email", TokenBasedVerification.class)
            .setParameter("email", tempUser.getEmail())
            .list();
    return !results.isEmpty();
  }
}
