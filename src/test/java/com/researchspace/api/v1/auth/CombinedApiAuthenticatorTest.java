package com.researchspace.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
import com.researchspace.model.UserAuthenticationMethod;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.ApiAvailabilityHandler;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletRequest;

public class CombinedApiAuthenticatorTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock ApiKeyAuthenticator apiKeyAuthenticator;
  @Mock OAuthTokenAuthenticator oAuthAuthenticator;
  @Mock AnalyticsManager analyticsManager;
  @Mock ApiAvailabilityHandler apiAvailabilityHandler;
  @InjectMocks CombinedApiAuthenticator combinedApiAuthenticator;

  MockHttpServletRequest mockRequest;

  @Test
  public void testOAuthAuthoriseOK() {
    mockRequest = new MockHttpServletRequest();
    setMockRequestAuthorizationHeader();
    User user = TestFactory.createAnyUser("testOauthUser");
    when(apiAvailabilityHandler.isApiAvailableForUser(null)).thenReturn(true);
    when(oAuthAuthenticator.authenticate(mockRequest)).thenReturn(user);

    /* run authenticator code with request using internal UI token */
    user.setAuthenticatedBy(UserAuthenticationMethod.UI_OAUTH_TOKEN);
    User authenticatedUser = combinedApiAuthenticator.authenticate(mockRequest);
    assertEquals(user, authenticatedUser);
    verifyNoInteractions(apiKeyAuthenticator);
    verifyNoInteractions(analyticsManager);

    /* try again, with request being authenticated by external API oauth token, but
     * also with system setting not allowing external oauth connections  */
    user.setAuthenticatedBy(UserAuthenticationMethod.API_OAUTH_TOKEN);
    when(apiAvailabilityHandler.isOAuthAccessAllowed(user)).thenReturn(false);
    assertThrows(
        ApiAuthenticationException.class, () -> combinedApiAuthenticator.authenticate(mockRequest));
    verifyNoInteractions(apiKeyAuthenticator);
    verifyNoInteractions(analyticsManager);

    /* try again, now with system setting allowing external oauth connections */
    when(apiAvailabilityHandler.isOAuthAccessAllowed(user)).thenReturn(true);
    authenticatedUser = combinedApiAuthenticator.authenticate(mockRequest);
    assertEquals(user, authenticatedUser);
    verifyNoInteractions(apiKeyAuthenticator);
    verify(analyticsManager, times(1)).publicApiUsed(eq(user), eq(mockRequest));
  }

  @Test
  public void testApiKeyAuthoriseOK() {
    mockRequest = new MockHttpServletRequest();
    setMockRequestApiKeyHeader();
    User user = TestFactory.createAnyUser("testApiKeyUser");
    when(apiAvailabilityHandler.isApiAvailableForUser(null)).thenReturn(true);
    when(apiAvailabilityHandler.isApiAvailableForUser(user)).thenReturn(true);
    when(apiKeyAuthenticator.authenticate(mockRequest)).thenReturn(user);

    User authenticatedUser = combinedApiAuthenticator.authenticate(mockRequest);
    assertEquals(user, authenticatedUser);

    verifyNoInteractions(oAuthAuthenticator);
    verify(analyticsManager, times(1)).publicApiUsed(eq(user), eq(mockRequest));
  }

  private void setMockRequestApiKeyHeader() {
    mockRequest.addHeader("apiKey", "12345");
  }

  private void setMockRequestAuthorizationHeader() {
    mockRequest.addHeader("Authorization", "Bearer 54321");
  }
}
