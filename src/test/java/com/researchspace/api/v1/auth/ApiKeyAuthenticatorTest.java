package com.researchspace.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.testutils.TestFactory;
import java.util.Optional;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
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

  @BeforeEach
  public void setUp() throws Exception {
    mockRequest = new MockHttpServletRequest();

    // When running all tests, stale SecurityUtils sometimes make into these tests
    try {
      SecurityUtils.getSubject().logout();
    } catch (UnavailableSecurityManagerException ignored) {
    }
  }

  @Test
  public void testAuthoriseOK() {
    User enabled = TestFactory.createAnyUser("any");
    setUpExpectations(enabled);
    shiroAPIKeyAuthoriser.authenticate(mockRequest);
    assertTrue(shiroAPIKeyAuthoriser.loginOK);
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
