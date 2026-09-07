package com.researchspace.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.service.OAuthTokenManager;
import com.researchspace.service.OAuthTokenManager.UiTokenContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

class OAuthTokenAuthenticatorTest {

  @Test
  void missingAuthorizationHeaderProducesAuthenticationError() {
    OAuthTokenAuthenticator authenticator = new OAuthTokenAuthenticator();

    ApiAuthenticationException exception =
        assertThrows(
            ApiAuthenticationException.class,
            () -> authenticator.retrieveTokenFromHeader(new MockHttpServletRequest()));

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
}
