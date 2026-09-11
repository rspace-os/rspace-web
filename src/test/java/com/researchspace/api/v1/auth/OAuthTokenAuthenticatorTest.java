package com.researchspace.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.auth.StatelessApiLogin;
import com.researchspace.model.User;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.testutils.TestFactory;
import org.apache.shiro.session.Session;
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
class OAuthTokenAuthenticatorTest {
  private static final String ACCESS_TOKEN = "access-token";

  @Mock OAuthTokenManager tokenManager;
  @InjectMocks OAuthTokenAuthenticator authenticator;

  private MockHttpServletRequest request;
  private Subject subject;
  private Session session;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + ACCESS_TOKEN);
    subject = Mockito.mock(Subject.class);
    session = Mockito.mock(Session.class);
    ThreadContext.remove();
    ThreadContext.bind(subject);
  }

  @AfterEach
  void tearDown() {
    ThreadContext.remove();
  }

  @Test
  void matchingUiTokenReusesAuthenticatedSession() {
    User user = TestFactory.createAnyUser("matching");
    givenTokenFor(user, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn(user.getUsername());
    when(subject.getSession()).thenReturn(session);
    when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(user);

    assertSame(user, authenticator.authenticate(request));
    verify(subject, never()).login(any());
  }

  @Test
  void mismatchedUiTokenIsRejected() {
    User tokenUser = TestFactory.createAnyUser("token-user");
    User sessionUser = TestFactory.createAnyUser("session-user");
    givenTokenFor(tokenUser, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn(sessionUser.getUsername());
    when(subject.getSession()).thenReturn(session);
    when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(sessionUser);

    ApiAuthenticationException exception =
        assertThrows(ApiAuthenticationException.class, () -> authenticator.authenticate(request));

    assertEquals("api.errors.authentication.oauthTokenInvalid", exception.getMessageKey());
    verify(subject, never()).login(any());
  }

  @Test
  void runAsDoesNotAllowMismatchedUiToken() {
    User tokenUser = TestFactory.createAnyUser("token-user");
    User sessionUser = TestFactory.createAnyUser("session-user");
    givenTokenFor(tokenUser, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn(sessionUser.getUsername());
    when(subject.getSession()).thenReturn(session);
    when(session.getAttribute(SessionAttributeUtils.USER)).thenReturn(sessionUser);

    assertEquals(
        "api.errors.authentication.oauthTokenInvalid",
        assertThrows(ApiAuthenticationException.class, () -> authenticator.authenticate(request))
            .getMessageKey());
  }

  @Test
  void uiTokenWithoutAuthenticatedBrowserSessionIsRejected() {
    User tokenUser = TestFactory.createAnyUser("token-user");
    givenTokenFor(tokenUser, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn(null);

    assertEquals(
        "api.errors.authentication.oauthTokenInvalid",
        assertThrows(ApiAuthenticationException.class, () -> authenticator.authenticate(request))
            .getMessageKey());
  }

  @Test
  void externalOAuthTokenUsesStatelessLoginWithoutBrowserSession() {
    User user = TestFactory.createAnyUser("external");
    givenTokenFor(user, OAuthTokenType.API_GENERATED_TOKEN);

    authenticator.authenticate(request);

    verify(subject).login(any());
    assertEquals(false, StatelessApiLogin.isInProgress());
  }

  @Test
  void missingAuthorizationHeaderProducesAuthenticationError() {
    ApiAuthenticationException exception =
        assertThrows(
            ApiAuthenticationException.class,
            () -> authenticator.retrieveTokenFromHeader(new MockHttpServletRequest()));

    assertEquals("api.errors.authentication.oauthHeaderInvalid", exception.getMessageKey());
  }

  private void givenTokenFor(User user, OAuthTokenType tokenType) {
    OAuthToken token = new OAuthToken(user, "client", tokenType);
    when(tokenManager.validateToken(ACCESS_TOKEN))
        .thenReturn(new ServiceOperationResult<>(null, true));
    when(tokenManager.authenticate(ACCESS_TOKEN))
        .thenReturn(new ServiceOperationResult<>(token, true));
  }
}
