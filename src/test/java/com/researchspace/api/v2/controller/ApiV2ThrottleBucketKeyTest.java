package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiV2ThrottleBucketKeyTest {

  private final ApiV2AbstractThrottleInterceptor interceptor =
      new ApiV2AbstractThrottleInterceptor();

  @Test
  void normalizesAndFingerprintsOAuthCredentials() {
    MockHttpServletRequest first = requestFrom("192.0.2.1");
    first.addHeader("Authorization", "Bearer token-value");
    MockHttpServletRequest second = requestFrom("192.0.2.2");
    second.addHeader("Authorization", "Bearer    token-value  ");

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

  private static MockHttpServletRequest requestFrom(String address) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(address);
    return request;
  }
}
