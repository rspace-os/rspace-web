package com.researchspace.api.v1.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

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
}
