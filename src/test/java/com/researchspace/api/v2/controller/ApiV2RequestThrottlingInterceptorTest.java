package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.throttling.APIRequestThrottler;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import com.researchspace.model.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiV2RequestThrottlingInterceptorTest {

  @Test
  void anonymousTrafficDoesNotConsumeAuthenticatedCapacity() {
    APIRequestThrottler client = mock(APIRequestThrottler.class);
    APIRequestThrottler global = mock(APIRequestThrottler.class);
    ApiV2RequestThrottlingInterceptor interceptor =
        new ApiV2RequestThrottlingInterceptor(client, global);

    boolean allowed =
        interceptor.preHandle(
            new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

    assertTrue(allowed);
    verify(client, never()).proceed(anyString());
    verify(global, never()).proceed(anyString());
  }

  @Test
  void preAuthenticationTrafficConsumesOnlyItsSourceBucket() {
    APIRequestThrottler preAuthentication = mock(APIRequestThrottler.class);
    when(preAuthentication.proceed(anyString())).thenReturn(true);
    ApiV2PreAuthenticationThrottlingInterceptor interceptor =
        new ApiV2PreAuthenticationThrottlingInterceptor(preAuthentication);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.0.2.10");
    request.addHeader("apiKey", "attacker-controlled-value");

    assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

    verify(preAuthentication).proceed(interceptor.assertApiAccess(request));
  }

  @Test
  void alwaysConsumesTheGlobalAdmissionBucketWhenTheClientBucketIsExhausted() {
    APIRequestThrottler client = mock(APIRequestThrottler.class);
    APIRequestThrottler global = mock(APIRequestThrottler.class);
    when(client.getStats(anyString(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.empty());
    when(client.proceed(anyString())).thenReturn(false);
    when(global.proceed(ApiV2RequestThrottlingInterceptor.GLOBAL_ALLOWANCE_KEY)).thenReturn(true);
    ApiV2RequestThrottlingInterceptor interceptor =
        new ApiV2RequestThrottlingInterceptor(client, global);

    boolean allowed =
        interceptor.preHandle(authenticatedRequest(), new MockHttpServletResponse(), new Object());

    assertFalse(allowed);
    verify(global).proceed(ApiV2RequestThrottlingInterceptor.GLOBAL_ALLOWANCE_KEY);
  }

  @Test
  void consumesTheGlobalBucketWhenClientExhaustionThrows() {
    APIRequestThrottler client = mock(APIRequestThrottler.class);
    APIRequestThrottler global = mock(APIRequestThrottler.class);
    when(client.getStats(anyString(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(Optional.empty());
    when(client.proceed(anyString())).thenThrow(new TooManyRequestsException("client exhausted"));
    when(global.proceed(ApiV2RequestThrottlingInterceptor.GLOBAL_ALLOWANCE_KEY)).thenReturn(true);
    ApiV2RequestThrottlingInterceptor interceptor =
        new ApiV2RequestThrottlingInterceptor(client, global);

    assertThrows(
        TooManyRequestsException.class,
        () ->
            interceptor.preHandle(
                authenticatedRequest(), new MockHttpServletResponse(), new Object()));

    verify(global).proceed(ApiV2RequestThrottlingInterceptor.GLOBAL_ALLOWANCE_KEY);
  }

  private static MockHttpServletRequest authenticatedRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    User user = mock(User.class);
    when(user.getId()).thenReturn(42L);
    request.setAttribute("user", user);
    return request;
  }
}
