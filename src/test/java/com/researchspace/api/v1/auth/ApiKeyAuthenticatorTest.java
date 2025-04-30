package com.researchspace.api.v1.auth;

import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.UserApiKeyManager;
import java.util.Optional;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
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
  @InjectMocks ApiKeyAuthenticatorTSS_Spy shiroAPIKeyAuthoriser;

  final String apiKey = "abcde";

  MockHttpServletRequest mockRequest;

  // replaces real shiro login with boolean test spy
  static class ApiKeyAuthenticatorTSS_Spy extends ApiKeyAuthenticator {
    public ApiKeyAuthenticatorTSS_Spy(UserApiKeyManager apiMgr) {
      super(apiMgr);
    }

    boolean loginOK = false;

    @Override
    void doLogin(String apiKey, User u) {
      loginOK = true;
    }
  }

  @Before
  public void setUp() throws Exception {
    mockRequest = new MockHttpServletRequest();

    // When running all tests, stale SecurityUtils sometimes make into these tests
    try {
      SecurityUtils.getSubject().logout();
    } catch (UnavailableSecurityManagerException ignored) {
    }
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testAuthoriseOK() {
    User enabled = TestFactory.createAnyUser("any");
    setUpExpectations(enabled);
    shiroAPIKeyAuthoriser.authenticate(mockRequest);
    assertTrue(shiroAPIKeyAuthoriser.loginOK);
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
