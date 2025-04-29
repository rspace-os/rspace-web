package com.researchspace.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
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
  public void testOauthAuthoriseOK() {
    mockRequest = new MockHttpServletRequest();
    User user = TestFactory.createAnyUser("testOauthUser");
    when(apiAvailabilityHandler.isApiAvailableForUser(null)).thenReturn(true);
    when(oAuthAuthenticator.authenticate(mockRequest)).thenReturn(user);

    setAuthorizationHeader();
    User authenticatedUser = combinedApiAuthenticator.authenticate(mockRequest);
    assertEquals(user, authenticatedUser);

    verifyNoInteractions(apiKeyAuthenticator);
    /* currently no event on oauth token usage, only on generation */
    verifyNoInteractions(analyticsManager);
  }

  @Test
  public void testApiKeyAuthoriseOK() {
    mockRequest = new MockHttpServletRequest();
    User user = TestFactory.createAnyUser("testApiKeyUser");
    when(apiAvailabilityHandler.isApiAvailableForUser(null)).thenReturn(true);
    when(apiAvailabilityHandler.isApiAvailableForUser(user)).thenReturn(true);
    when(apiKeyAuthenticator.authenticate(mockRequest)).thenReturn(user);

    setApiKeyHeader();
    User authenticatedUser = combinedApiAuthenticator.authenticate(mockRequest);
    assertEquals(user, authenticatedUser);

    verifyNoInteractions(oAuthAuthenticator);
    verify(analyticsManager, times(1)).apiAccessed(eq(user), eq(true), eq(mockRequest));
  }

  private void setApiKeyHeader() {
    mockRequest.addHeader("apiKey", "12345");
  }

  private void setAuthorizationHeader() {
    mockRequest.addHeader("Authorization", "Bearer 54321");
  }
}
