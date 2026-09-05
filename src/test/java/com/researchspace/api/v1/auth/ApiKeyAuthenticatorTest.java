package com.researchspace.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.auth.StatelessApiLogin;
import com.researchspace.model.User;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.testutils.TestFactory;
import java.util.Optional;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
public class ApiKeyAuthenticatorTest {
  @Mock UserApiKeyManager apiMgr;
  @InjectMocks ApiKeyAuthenticator shiroAPIKeyAuthoriser;

  final String apiKey = "abcde";

  MockHttpServletRequest mockRequest;

  boolean loginOK = false;
  boolean statelessScopeActive = false;

  @BeforeEach
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

  @AfterEach
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

  @Test
  public void testAuthoriseFailsIfUserDisabled() {
    User disabled = TestFactory.createAnyUser("any");
    disabled.setEnabled(false);
    setUpExpectations(disabled);
    assertThrows(
        ApiAuthenticationException.class, () -> shiroAPIKeyAuthoriser.authenticate(mockRequest));
  }

  @Test
  public void testAuthoriseFailsIfUserLocked() {
    User locked = TestFactory.createAnyUser("any");
    locked.setAccountLocked(true);
    setUpExpectations(locked);
    assertThrows(
        ApiAuthenticationException.class, () -> shiroAPIKeyAuthoriser.authenticate(mockRequest));
  }

  @Test
  public void nonMatchingKeyThrowsAuthException() {
    User enabled = TestFactory.createAnyUser("any");
    setApiKeyHeader();
    Mockito.when(apiMgr.findUserByKey(apiKey)).thenReturn(Optional.ofNullable(null));
    assertThrows(
        ApiAuthenticationException.class, () -> shiroAPIKeyAuthoriser.authenticate(mockRequest));
  }

  private void setUpExpectations(User user) {
    Mockito.when(apiMgr.findUserByKey(apiKey)).thenReturn(Optional.of(user));
    setApiKeyHeader();
  }

  private void setApiKeyHeader() {
    mockRequest.addHeader("apiKey", apiKey);
  }
}
