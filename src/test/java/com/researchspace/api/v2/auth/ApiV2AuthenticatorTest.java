package com.researchspace.api.v2.auth;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.UserApiKeyManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiV2AuthenticatorTest {

  private final UserApiKeyManager apiKeyManager = mock(UserApiKeyManager.class);
  private final OAuthTokenManager oAuthTokenManager = mock(OAuthTokenManager.class);
  private final ApiAvailabilityHandler availability = mock(ApiAvailabilityHandler.class);
  private final AnalyticsManager analytics = mock(AnalyticsManager.class);
  private final ApiV2Authenticator authenticator =
      new ApiV2Authenticator(apiKeyManager, oAuthTokenManager, analytics, availability);

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

    assertSame(user, authenticator.authenticate(request));

    verify(user).setAuthenticatedBy(UserAuthenticationMethod.API_KEY);
    verify(analytics).publicApiUsed(user, request);
  }

  @Test
  void uiOAuthUsesOnlyTheBearerTokenIdentity() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/users/me");
    request.addHeader("Authorization", "Bearer ui-token");
    request.addHeader("Cookie", "JSESSIONID=unrelated-browser-session");
    User tokenUser = mock(User.class);
    validUiToken("ui-token", tokenUser);

    assertSame(tokenUser, authenticator.authenticate(request));

    verify(tokenUser).setAuthenticatedBy(UserAuthenticationMethod.UI_OAUTH_TOKEN);
  }

  private void validUiToken(String tokenValue, User user) {
    OAuthToken token = new OAuthToken(user, "browser", OAuthTokenType.UI_TOKEN);
    when(oAuthTokenManager.validateToken(tokenValue))
        .thenReturn(new ServiceOperationResult<>(null, true, "valid"));
    when(oAuthTokenManager.authenticate(tokenValue))
        .thenReturn(new ServiceOperationResult<>(token, true, "authenticated"));
  }
}
