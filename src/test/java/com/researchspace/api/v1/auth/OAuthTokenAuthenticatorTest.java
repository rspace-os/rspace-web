package com.researchspace.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.api.v2.auth.ApiV2Authenticator;
import com.researchspace.api.v2.auth.ApiV2BrowserSessionAuthenticator;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.auth.BrowserSessionAuthContext;
import com.researchspace.auth.StatelessApiLogin;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.OAuthTokenManager.UiTokenContext;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.testutils.TestFactory;
import java.util.Optional;
import org.apache.shiro.session.InvalidSessionException;
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
import org.springframework.test.util.ReflectionTestUtils;

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
  void matchingUiTokenReturnsFreshTargetUser() {
    User targetUser = TestFactory.createAnyUser("matching");
    givenTokenFor(targetUser, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn(targetUser.getUsername());
    when(subject.getSession(false)).thenReturn(session);

    assertSame(targetUser, authenticator.authenticate(request));
    verify(subject, never()).login(any());
    verify(session, never()).getAttribute(SessionAttributeUtils.USER);
  }

  @Test
  void uiTokenMatchesPrincipalThatDiffersOnlyInCase() {
    User targetUser = TestFactory.createAnyUser("jsmith");
    givenTokenFor(targetUser, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn("JSmith");
    when(subject.getSession(false)).thenReturn(session);

    assertSame(targetUser, authenticator.authenticate(request));
    verify(subject, never()).login(any());
  }

  @Test
  void mismatchedUiTokenIsRejected() {
    User tokenUser = TestFactory.createAnyUser("token-user");
    User sessionUser = TestFactory.createAnyUser("session-user");
    givenTokenFor(tokenUser, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn(sessionUser.getUsername());
    when(subject.getSession(false)).thenReturn(session);

    ApiAuthenticationException exception =
        assertThrows(ApiAuthenticationException.class, () -> authenticator.authenticate(request));

    assertEquals("api.errors.authentication.oauthTokenInvalid", exception.getMessageKey());
    verify(subject, never()).login(any());
    verify(session, never()).getAttribute(SessionAttributeUtils.USER);
  }

  @Test
  void uiTokenWithoutAuthenticatedPrincipalIsRejected() {
    User tokenUser = TestFactory.createAnyUser("token-user");
    givenTokenFor(tokenUser, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn(null);

    assertEquals(
        "api.errors.authentication.oauthTokenInvalid",
        assertThrows(ApiAuthenticationException.class, () -> authenticator.authenticate(request))
            .getMessageKey());
    verify(subject, never()).getSession(false);
  }

  @Test
  void uiTokenWithoutAuthenticatedBrowserSessionIsRejected() {
    User tokenUser = TestFactory.createAnyUser("token-user");
    givenTokenFor(tokenUser, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn(tokenUser.getUsername());
    when(subject.getSession(false)).thenReturn(null);

    assertEquals(
        "api.errors.authentication.oauthTokenInvalid",
        assertThrows(ApiAuthenticationException.class, () -> authenticator.authenticate(request))
            .getMessageKey());
  }

  @Test
  void uiTokenWithExpiredBrowserSessionIsRejected() {
    User tokenUser = TestFactory.createAnyUser("token-user");
    givenTokenFor(tokenUser, OAuthTokenType.UI_TOKEN);
    when(subject.getPrincipal()).thenReturn(tokenUser.getUsername());
    when(subject.getSession(false)).thenThrow(new InvalidSessionException());

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
    OAuthTokenAuthenticator authenticator = new OAuthTokenAuthenticator();
    MockHttpServletRequest request = new MockHttpServletRequest();

    ApiAuthenticationException exception =
        assertThrows(
            ApiAuthenticationException.class, () -> authenticator.retrieveTokenFromHeader(request));

    assertEquals("api.errors.authentication.oauthHeaderInvalid", exception.getMessageKey());
  }

  @Test
  void sessionBoundApiV2TokenIsNotAcceptedAsAnApiV1BearerToken() {
    when(tokenManager.getUiTokenContext("v2-ui-token"))
        .thenReturn(Optional.of(new UiTokenContext(42L, Optional.empty(), "session-context")));

    assertTrue(authenticator.findUserForToken().apply("v2-ui-token").isEmpty());
    verify(tokenManager, never()).authenticate("v2-ui-token");
  }

  private void givenTokenFor(User user, OAuthTokenType tokenType) {
    OAuthToken token = new OAuthToken(user, "client", tokenType);
    when(tokenManager.validateToken(ACCESS_TOKEN))
        .thenReturn(new ServiceOperationResult<>(null, true));
    when(tokenManager.authenticate(ACCESS_TOKEN))
        .thenReturn(new ServiceOperationResult<>(token, true));
  }

  @Test
  void authenticatesDirectSessionBoundUiTokenThroughTheApiV2Validator() {
    User subject = mock(User.class);
    when(subject.getId()).thenReturn(42L);
    MockHttpServletRequest request = requestWithCurrentBrowserContext();
    String context = BrowserSessionAuthContext.current(request.getSession()).orElseThrow();
    ApiV2Caller caller = ApiV2Caller.direct(subject);
    RecordingOAuthTokenAuthenticator authenticator =
        authenticatorForUiToken(
            request,
            subject,
            Optional.of(caller),
            new UiTokenContext(42L, Optional.empty(), context));

    assertSame(subject, authenticator.authenticate(request));

    verify(subject).setAuthenticatedBy(UserAuthenticationMethod.UI_OAUTH_TOKEN);
    assertEquals(0, authenticator.statelessLoginCount);
  }

  @Test
  void authenticatesDelegatedSessionBoundUiTokenWithoutStartingAnotherShiroLogin() {
    User subject = mock(User.class);
    User actor = mock(User.class);
    when(subject.getId()).thenReturn(42L);
    when(actor.getId()).thenReturn(7L);
    MockHttpServletRequest request = requestWithCurrentBrowserContext();
    String context = BrowserSessionAuthContext.current(request.getSession()).orElseThrow();
    ApiV2Caller caller = new ApiV2Caller(subject, actor);
    RecordingOAuthTokenAuthenticator authenticator =
        authenticatorForUiToken(
            request,
            subject,
            Optional.of(caller),
            new UiTokenContext(42L, Optional.of(7L), context));

    assertSame(subject, authenticator.authenticate(request));

    verify(subject).setAuthenticatedBy(UserAuthenticationMethod.UI_OAUTH_TOKEN);
    assertEquals(0, authenticator.statelessLoginCount);
  }

  @Test
  void rejectsSessionBoundUiTokenWithoutItsBrowserSessionContext() {
    User subject = mock(User.class);
    when(subject.getId()).thenReturn(42L);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/inventory");
    request.addHeader("Authorization", "Bearer v2-ui-token");
    RecordingOAuthTokenAuthenticator authenticator =
        authenticatorForUiToken(
            request,
            subject,
            Optional.empty(),
            new UiTokenContext(42L, Optional.empty(), "session-context"));

    ApiAuthenticationException exception =
        assertThrows(ApiAuthenticationException.class, () -> authenticator.authenticate(request));

    assertEquals("api.errors.authentication.oauthTokenInvalid", exception.getMessageKey());
    assertEquals(0, authenticator.statelessLoginCount);
  }

  @Test
  void rejectsUiTokenCopiedFromAnotherBrowserSession() {
    User subject = mock(User.class);
    when(subject.getId()).thenReturn(42L);
    MockHttpServletRequest request = requestWithCurrentBrowserContext();
    RecordingOAuthTokenAuthenticator authenticator =
        authenticatorForUiToken(
            request,
            subject,
            Optional.of(ApiV2Caller.direct(subject)),
            new UiTokenContext(42L, Optional.empty(), "another-browser-session"));

    ApiAuthenticationException exception =
        assertThrows(ApiAuthenticationException.class, () -> authenticator.authenticate(request));

    assertEquals("api.errors.authentication.oauthTokenInvalid", exception.getMessageKey());
    assertEquals(0, authenticator.statelessLoginCount);
  }

  @Test
  void rejectsSignedUiTokenWhenAnApiKeyWouldTakePrecedenceInApiV2() {
    User subject = mock(User.class);
    when(subject.getId()).thenReturn(42L);
    MockHttpServletRequest request = requestWithCurrentBrowserContext();
    request.addHeader("apiKey", "another-users-api-key");
    String context = BrowserSessionAuthContext.current(request.getSession()).orElseThrow();
    RecordingOAuthTokenAuthenticator authenticator =
        authenticatorForUiToken(
            request,
            subject,
            Optional.of(ApiV2Caller.direct(subject)),
            new UiTokenContext(42L, Optional.empty(), context));

    ApiAuthenticationException exception =
        assertThrows(ApiAuthenticationException.class, () -> authenticator.authenticate(request));

    assertEquals("api.errors.authentication.oauthTokenInvalid", exception.getMessageKey());
    assertEquals(0, authenticator.statelessLoginCount);
  }

  @Test
  void keepsDirectApiOAuthTokensOnTheLegacyAuthenticationPath() {
    User user = mock(User.class);
    when(user.isLoginDisabled()).thenReturn(false);
    OAuthTokenManager tokenManager = mock(OAuthTokenManager.class);
    when(tokenManager.validateToken("api-token"))
        .thenReturn(new ServiceOperationResult<>(null, true, "valid"));
    when(tokenManager.getUiTokenContext("api-token")).thenReturn(Optional.empty());
    when(tokenManager.authenticate("api-token"))
        .thenReturn(
            new ServiceOperationResult<>(
                new OAuthToken(user, "api-client", OAuthTokenType.API_GENERATED_TOKEN),
                true,
                "authenticated"));
    RecordingOAuthTokenAuthenticator authenticator = new RecordingOAuthTokenAuthenticator();
    ReflectionTestUtils.setField(authenticator, "tokenManager", tokenManager);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/inventory");
    request.addHeader("Authorization", "Bearer api-token");

    assertSame(user, authenticator.authenticate(request));

    verify(user).setAuthenticatedBy(UserAuthenticationMethod.API_OAUTH_TOKEN);
    assertEquals(1, authenticator.statelessLoginCount);
  }

  private RecordingOAuthTokenAuthenticator authenticatorForUiToken(
      MockHttpServletRequest request,
      User subject,
      Optional<ApiV2Caller> caller,
      UiTokenContext context) {
    OAuthTokenManager tokenManager = mock(OAuthTokenManager.class);
    when(tokenManager.validateToken("v2-ui-token"))
        .thenReturn(new ServiceOperationResult<>(null, true, "valid"));
    when(tokenManager.authenticate("v2-ui-token"))
        .thenReturn(
            new ServiceOperationResult<>(
                new OAuthToken(subject, "browser", OAuthTokenType.UI_TOKEN),
                true,
                "authenticated"));
    when(tokenManager.getUiTokenContext("v2-ui-token")).thenReturn(Optional.of(context));
    ApiV2BrowserSessionAuthenticator browserSessionAuthenticator =
        mock(ApiV2BrowserSessionAuthenticator.class);
    when(browserSessionAuthenticator.authenticateIfPresent(request)).thenReturn(caller);
    ApiV2Authenticator apiV2Authenticator =
        apiV2Authenticator(tokenManager, browserSessionAuthenticator);
    RecordingOAuthTokenAuthenticator authenticator = new RecordingOAuthTokenAuthenticator();
    ReflectionTestUtils.setField(authenticator, "tokenManager", tokenManager);
    ReflectionTestUtils.setField(authenticator, "apiV2Authenticator", apiV2Authenticator);
    return authenticator;
  }

  private ApiV2Authenticator apiV2Authenticator(
      OAuthTokenManager tokenManager,
      ApiV2BrowserSessionAuthenticator browserSessionAuthenticator) {
    return new ApiV2Authenticator(
        mock(UserApiKeyManager.class),
        tokenManager,
        mock(AnalyticsManager.class),
        mock(ApiAvailabilityHandler.class),
        browserSessionAuthenticator);
  }

  private MockHttpServletRequest requestWithCurrentBrowserContext() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/inventory");
    request.addHeader("Authorization", "Bearer v2-ui-token");
    BrowserSessionAuthContext.rotate(request.getSession());
    return request;
  }

  private static class RecordingOAuthTokenAuthenticator extends OAuthTokenAuthenticator {
    private int statelessLoginCount;

    @Override
    void doLogin(String apiKey, User user) {
      statelessLoginCount++;
    }
  }
}
