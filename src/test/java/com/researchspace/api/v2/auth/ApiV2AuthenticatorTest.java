package com.researchspace.api.v2.auth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.auth.BrowserSessionAuthContext;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.OAuthTokenManager.UiTokenContext;
import com.researchspace.service.UserApiKeyManager;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiV2AuthenticatorTest {

  private final UserApiKeyManager apiKeyManager = mock(UserApiKeyManager.class);
  private final OAuthTokenManager oAuthTokenManager = mock(OAuthTokenManager.class);
  private final ApiAvailabilityHandler availability = mock(ApiAvailabilityHandler.class);
  private final AnalyticsManager analytics = mock(AnalyticsManager.class);
  private final ApiV2BrowserSessionAuthenticator browserSessionAuthenticator =
      mock(ApiV2BrowserSessionAuthenticator.class);
  private final ApiV2Authenticator authenticator =
      new ApiV2Authenticator(
          apiKeyManager, oAuthTokenManager, analytics, availability, browserSessionAuthenticator);

  @Test
  void rejectsMissingCredentials() {
    assertThrows(
        ApiV2AuthenticationException.class,
        () -> authenticator.authenticate(new MockHttpServletRequest()));
  }

  @Test
  void optionalAuthenticationReturnsEmptyForAnAnonymousRequest() {
    assertTrue(authenticator.authenticateIfPresent(new MockHttpServletRequest()).isEmpty());
  }

  @Test
  void optionalAuthenticationIgnoresBrowserSessionCookies() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Cookie", "JSESSIONID=existing-browser-session");

    assertTrue(authenticator.authenticateIfPresent(request).isEmpty());
  }

  @Test
  void authenticatesApiKeyWithoutUsingV1Authentication() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/users/me");
    request.addHeader("apiKey", "abcde");
    User user = mock(User.class);
    when(user.getUsername()).thenReturn("ada");
    when(apiKeyManager.findUserByKey("abcde")).thenReturn(Optional.of(user));
    when(availability.isApiAvailableForUser(null)).thenReturn(true);
    when(availability.isApiAvailableForUser(user)).thenReturn(true);

    assertSame(user, authenticator.authenticate(request).subject());

    verify(user).setAuthenticatedBy(UserAuthenticationMethod.API_KEY);
    verify(analytics).publicApiUsed(user, request);
  }

  @Test
  void securityLogsAuthenticationFailuresWithoutLoggingTheCredential() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/users/me");
    request.setRemoteAddr("192.0.2.10");
    request.addHeader("apiKey", "raw-secret-that-must-not-be-logged");
    when(availability.isApiAvailableForUser(null)).thenReturn(true);
    StringAppenderForTestLogging securityLog =
        CoreTestUtils.configureStringLogger(LogManager.getLogger(SecurityLogger.class));

    assertThrows(ApiV2AuthenticationException.class, () -> authenticator.authenticate(request));

    assertTrue(securityLog.logContents.contains("REST API v2 authentication failed"));
    assertTrue(securityLog.logContents.contains("/api/v2/users/me"));
    assertTrue(!securityLog.logContents.contains("raw-secret-that-must-not-be-logged"));
  }

  @Test
  void uiOAuthRequiresTheMatchingLiveBrowserIdentityAndContext() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/users/me");
    request.addHeader("Authorization", "Bearer ui-token");
    BrowserSessionAuthContext.rotate(request.getSession());
    User tokenUser = mock(User.class);
    when(tokenUser.getId()).thenReturn(41L);
    validUiToken("ui-token", tokenUser);
    when(oAuthTokenManager.getUiTokenContext("ui-token"))
        .thenReturn(
            Optional.of(
                new UiTokenContext(
                    41L,
                    Optional.empty(),
                    BrowserSessionAuthContext.current(request.getSession()).orElseThrow())));
    when(browserSessionAuthenticator.authenticateIfPresent(request))
        .thenReturn(Optional.of(ApiV2Caller.direct(tokenUser)));

    assertSame(tokenUser, authenticator.authenticate(request).subject());

    verify(tokenUser).setAuthenticatedBy(UserAuthenticationMethod.UI_OAUTH_TOKEN);
  }

  @Test
  void rejectsAUiTokenAfterTheSessionContextRotates() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/users/me");
    request.addHeader("Authorization", "Bearer stale-ui-token");
    request.getSession();
    BrowserSessionAuthContext.rotate(request.getSession());
    User tokenUser = mock(User.class);
    when(tokenUser.getId()).thenReturn(41L);
    validUiToken("stale-ui-token", tokenUser);
    when(oAuthTokenManager.getUiTokenContext("stale-ui-token"))
        .thenReturn(Optional.of(new UiTokenContext(41L, Optional.empty(), "previous-context")));
    when(browserSessionAuthenticator.authenticateIfPresent(request))
        .thenReturn(Optional.of(ApiV2Caller.direct(tokenUser)));

    assertThrows(ApiV2AuthenticationException.class, () -> authenticator.authenticate(request));
  }

  private void validUiToken(String tokenValue, User user) {
    OAuthToken token = new OAuthToken(user, "browser", OAuthTokenType.UI_TOKEN);
    when(oAuthTokenManager.validateToken(tokenValue))
        .thenReturn(new ServiceOperationResult<>(null, true, "valid"));
    when(oAuthTokenManager.authenticate(tokenValue))
        .thenReturn(new ServiceOperationResult<>(token, true, "authenticated"));
  }
}
