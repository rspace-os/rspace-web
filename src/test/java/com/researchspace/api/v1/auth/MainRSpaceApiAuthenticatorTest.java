package com.researchspace.api.v1.auth;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.analytics.service.AnalyticsManager;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.ApiAvailabilityHandler;
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

public class MainRSpaceApiAuthenticatorTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock UserApiKeyManager apiKeyMgr;
  @Mock OAuthTokenAuthenticator oAuthAuthenticator;
  @Mock AnalyticsManager analyticsManager;
  @Mock ApiAvailabilityHandler apiAvailabilityHandler;
  @InjectMocks MainRSpaceApiAuthenticator apiAuthenticator;

  MockHttpServletRequest mockRequest;

  @Test
  public void testOauthAuthoriseOK() {
    mockRequest = new MockHttpServletRequest();
    when(apiAvailabilityHandler.isApiAvailableForUser(null)).thenReturn(true);
    setAuthorizationHeader();
    User authenticatedUser = apiAuthenticator.authenticate(mockRequest);
    assertNull(authenticatedUser);
  }

  private void setApiKeyHeader() {
    mockRequest.addHeader("apiKey", "1234");
  }

  private void setAuthorizationHeader() {
    mockRequest.addHeader("Authorization", "Bearer 123");
  }

}
