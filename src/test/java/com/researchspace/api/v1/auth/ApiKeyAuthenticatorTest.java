package com.researchspace.api.v1.auth;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.auth.StatelessApiLogin;
import com.researchspace.model.User;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.testutils.TestFactory;
import java.util.Optional;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;

public class ApiKeyAuthenticatorTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock UserApiKeyManager apiMgr;
  @InjectMocks ApiKeyAuthenticator shiroAPIKeyAuthoriser;

  final String apiKey = "abcde";

  MockHttpServletRequest mockRequest;

  boolean loginOK = false;
  boolean statelessScopeActive = false;

  @Before
  public void setUp() throws Exception {
    mockRequest = new MockHttpServletRequest();

    Subject subject =
        Mockito.mock(
            Subject.class,
            invocation -> {
              if (invocation.getMethod().getName().equals("login")) {
                loginOK = true;
                statelessScopeActive = StatelessApiLogin.isInProgress();
              }
              return Mockito.RETURNS_DEFAULTS.answer(invocation);
            });

    // replace any stale thread state from other tests with a spy subject, so
    // SecurityUtils.getSubject() inside the authenticator resolves to it
    ThreadContext.remove();
    ThreadContext.bind(subject);
  }

  @After
  public void tearDown() throws Exception {
    ThreadContext.remove();
  }

  @Test
  public void testAuthoriseOK() {
    User enabled = TestFactory.createAnyUser("any");
    setUpExpectations(enabled);
    shiroAPIKeyAuthoriser.authenticate(mockRequest);
    assertTrue(loginOK);
  }

  @Test
  public void loginRunsInsideTheStatelessScope() {
    User enabled = TestFactory.createAnyUser("any");
    setUpExpectations(enabled);

    shiroAPIKeyAuthoriser.authenticate(mockRequest);

    assertTrue(statelessScopeActive);
    assertFalse(StatelessApiLogin.isInProgress());
  }

  @Test(expected = ApiAuthenticationException.class)
  public void testAuthoriseFailsIfUserDisabled() {
    User disabled = TestFactory.createAnyUser("any");
    disabled.setEnabled(false);
    setUpExpectations(disabled);
    shiroAPIKeyAuthoriser.authenticate(mockRequest);
  }

  @Test(expected = ApiAuthenticationException.class)
  public void testAuthoriseFailsIfUserLocked() {
    User locked = TestFactory.createAnyUser("any");
    locked.setAccountLocked(true);
    setUpExpectations(locked);
    shiroAPIKeyAuthoriser.authenticate(mockRequest);
  }

  @Test(expected = ApiAuthenticationException.class)
  public void nonMatchingKeyThrowsAuthException() {
    User enabled = TestFactory.createAnyUser("any");
    setApiKeyHeader();
    Mockito.when(apiMgr.findUserByKey(apiKey)).thenReturn(Optional.ofNullable(null));
    shiroAPIKeyAuthoriser.authenticate(mockRequest);
  }

  private void setUpExpectations(User user) {
    Mockito.when(apiMgr.findUserByKey(apiKey)).thenReturn(Optional.of(user));
    setApiKeyHeader();
  }

  private void setApiKeyHeader() {
    mockRequest.addHeader("apiKey", apiKey);
  }
}
