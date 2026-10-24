package com.researchspace.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.api.v2.auth.ApiV2Authenticator;
import com.researchspace.api.v2.auth.ApiV2BrowserSessionAuthenticator;
import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.auth.BrowserSessionAuthContext;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.OAuthTokenManager.UiTokenContext;
import com.researchspace.service.UserApiKeyManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class OAuthTokenAuthenticatorTest {

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
    OAuthTokenManager tokenManager = mock(OAuthTokenManager.class);
    when(tokenManager.getUiTokenContext("v2-ui-token"))
        .thenReturn(Optional.of(new UiTokenContext(42L, Optional.empty(), "session-context")));
    OAuthTokenAuthenticator authenticator = new OAuthTokenAuthenticator();
    ReflectionTestUtils.setField(authenticator, "tokenManager", tokenManager);

    assertTrue(authenticator.findUserForToken().apply("v2-ui-token").isEmpty());
    verify(tokenManager, never()).authenticate("v2-ui-token");
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
