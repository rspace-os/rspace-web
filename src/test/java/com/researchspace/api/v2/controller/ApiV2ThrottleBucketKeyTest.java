package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiV2ThrottleBucketKeyTest {

  private final ApiV2AbstractThrottleInterceptor interceptor =
      new ApiV2AbstractThrottleInterceptor();

  @Test
  void ignoresUnvalidatedCredentialsAndKeysPreAuthenticationBySource() {
    MockHttpServletRequest first = requestFrom("192.0.2.1");
    first.addHeader("Authorization", "Bearer token-value");
    MockHttpServletRequest second = requestFrom("192.0.2.1");
    second.addHeader("Authorization", "Bearer attacker-rotated-value");

    String firstKey = interceptor.assertApiAccess(first);
    String secondKey = interceptor.assertApiAccess(second);

    assertEquals(firstKey, secondKey);
    assertFalse(firstKey.contains("token-value"));
  }

  @Test
  void fingerprintsApiKeysInsteadOfRetainingCredentials() {
    MockHttpServletRequest request = requestFrom("192.0.2.1");
    request.addHeader("apiKey", "secret-api-key");

    String key = interceptor.assertApiAccess(request);

    assertFalse(key.contains("secret-api-key"));
  }

  @Test
  void separatesAnonymousCallersByClientAddress() {
    String first = interceptor.assertApiAccess(requestFrom("192.0.2.1"));
    String sameClient = interceptor.assertApiAccess(requestFrom("192.0.2.1"));
    String otherClient = interceptor.assertApiAccess(requestFrom("192.0.2.2"));

    assertEquals(first, sameClient);
    assertNotEquals(first, otherClient);
  }

  @Test
  void keysAuthenticatedRequestsByValidatedUserRatherThanCredentialOrAddress() {
    User user = mock(User.class);
    when(user.getId()).thenReturn(42L);
    MockHttpServletRequest first = requestFrom("192.0.2.1");
    first.addHeader("apiKey", "first-credential");
    first.setAttribute(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user));
    MockHttpServletRequest second = requestFrom("192.0.2.2");
    second.addHeader("Authorization", "Bearer second-credential");
    second.setAttribute(ApiV2Caller.REQUEST_ATTRIBUTE, ApiV2Caller.direct(user));

    assertEquals(interceptor.assertApiAccess(first), interceptor.assertApiAccess(second));
  }

  @Test
  void keysDelegatedRequestsByOriginalActorRatherThanEffectiveSubject() {
    User actor = mock(User.class);
    User firstSubject = mock(User.class);
    User secondSubject = mock(User.class);
    when(actor.getId()).thenReturn(1L);
    when(firstSubject.getId()).thenReturn(41L);
    when(secondSubject.getId()).thenReturn(42L);
    MockHttpServletRequest first = requestFrom("192.0.2.1");
    first.setAttribute(ApiV2Caller.REQUEST_ATTRIBUTE, new ApiV2Caller(firstSubject, actor));
    MockHttpServletRequest second = requestFrom("192.0.2.2");
    second.setAttribute(ApiV2Caller.REQUEST_ATTRIBUTE, new ApiV2Caller(secondSubject, actor));

    assertEquals(interceptor.assertApiAccess(first), interceptor.assertApiAccess(second));
  }

  private static MockHttpServletRequest requestFrom(String address) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(address);
    return request;
  }
}
