package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.throttling.APIRequestThrottler;
import com.researchspace.api.v1.throttling.APIUsageStats;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.webapp.filter.OriginRefererChecker;
import java.util.Optional;
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
import org.springframework.mock.web.MockHttpServletResponse;

public class APIThrottlingInterceptorTest {
  private static final String ANY_KEY = "any";
  @InjectMocks APIRequestThrottlingInterceptor apiThrottlingInterceptor;
  MockHttpServletRequest req;
  MockHttpServletResponse resp;

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock APIRequestThrottler userThrottler;
  @Mock APIRequestThrottler globalThrottler;
  @Mock APIRequestThrottler inventoryThrottler;
  @Mock OriginRefererChecker checker;

  @Before
  public void setUp() throws Exception {
    req = new MockHttpServletRequest();
    resp = new MockHttpServletResponse();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testPreHandle() throws Exception {
    apiThrottlingInterceptor.setUserThrottler(userThrottler);
    apiThrottlingInterceptor.setGlobalThrottler(globalThrottler);
    req.addHeader("apiKey", ANY_KEY);
    when(userThrottler.proceed(Mockito.anyString())).thenReturn(true);
    when(globalThrottler.proceed(Mockito.anyString())).thenReturn(true);
    assertTrue(apiThrottlingInterceptor.preHandle(req, resp, null));
  }

  @Test
  public void testHandleAnonymousRequests() throws Exception {
    assertEquals("anonymousApiUser", apiThrottlingInterceptor.assertApiAccess(req));
    req.addHeader("apiKey", ANY_KEY);
    assertEquals(ANY_KEY, apiThrottlingInterceptor.assertApiAccess(req));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testGenerateInvHeaders() throws Exception {
    apiThrottlingInterceptor.setInventoryThrottler(inventoryThrottler);
    String identifierString = "abc";
    // 0 min wait time
    APIUsageStats hour =
        APIUsageStats.builder()
            .periodSeconds(3600)
            .minDelayTillNextRequestMillis(0)
            .remainingRequestsInPeriod(2000.0f)
            .totalRequestsPerPeriod(2000)
            .build();
    APIUsageStats hourExceeded =
        APIUsageStats.builder()
            .periodSeconds(3600)
            .minDelayTillNextRequestMillis(0)
            .remainingRequestsInPeriod(0.001f)
            .totalRequestsPerPeriod(1800)
            .build();
    when(inventoryThrottler.getStats(
            Mockito.eq(identifierString), Mockito.eq(ThrottleInterval.HOUR)))
        .thenReturn(Optional.of(hour), Optional.of(hourExceeded));
    req.setRequestURI("/api/inventory/v1/samples");
    req.addHeader("apiKey", identifierString);

    // ok
    apiThrottlingInterceptor.preHandle(req, resp, null);
    assertEquals("0", resp.getHeader("X-Rate-Limit-WaitTimeMillis"));

    // limit exceeded, need to wait almost 3600/1800 seconds
    apiThrottlingInterceptor.preHandle(req, resp, null);
    assertEquals("1999", resp.getHeader("X-Rate-Limit-WaitTimeMillis"));
  }

  @Test
  public void testGenerateHeaders() throws Exception {
    apiThrottlingInterceptor.setUserThrottler(userThrottler);
    String identifierString = "abc";
    // remaining requests are OK, so min wait is min wait
    APIUsageStats quarterMin =
        APIUsageStats.builder()
            .periodSeconds(15)
            .minDelayTillNextRequestMillis(100)
            .remainingRequestsInPeriod(8.5f)
            .totalRequestsPerPeriod(15)
            .build();
    APIUsageStats hour =
        APIUsageStats.builder()
            .periodSeconds(3600)
            .minDelayTillNextRequestMillis(100)
            .remainingRequestsInPeriod(2000.0f)
            .totalRequestsPerPeriod(2000)
            .build();
    APIUsageStats day =
        APIUsageStats.builder()
            .periodSeconds(86400)
            .minDelayTillNextRequestMillis(100)
            .remainingRequestsInPeriod(20000.0f)
            .totalRequestsPerPeriod(50000)
            .build();
    when(userThrottler.getStats(
            Mockito.eq(identifierString), Mockito.eq(ThrottleInterval.QUARTER_MIN)))
        .thenReturn(Optional.of(quarterMin));
    when(userThrottler.getStats(Mockito.eq(identifierString), Mockito.eq(ThrottleInterval.HOUR)))
        .thenReturn(Optional.of(hour));
    when(userThrottler.getStats(Mockito.eq(identifierString), Mockito.eq(ThrottleInterval.DAY)))
        .thenReturn(Optional.of(day));
    apiThrottlingInterceptor.setUsageHeaderStats(req, resp, identifierString);
    assertEquals("100", resp.getHeader("X-Rate-Limit-WaitTimeMillis"));

    // we've used up 15s bucket, have to wait
    APIUsageStats quarterMin2 =
        APIUsageStats.builder()
            .periodSeconds(15)
            .minDelayTillNextRequestMillis(100)
            .remainingRequestsInPeriod(0.1f)
            .totalRequestsPerPeriod(15)
            .build();
    when(userThrottler.getStats(
            Mockito.eq(identifierString), Mockito.eq(ThrottleInterval.QUARTER_MIN)))
        .thenReturn(Optional.of(quarterMin2));
    apiThrottlingInterceptor.setUsageHeaderStats(req, resp, identifierString);
    assertEquals("901", resp.getHeader("X-Rate-Limit-WaitTimeMillis"));

    // we've used up 1h bucket, have to wait
    APIUsageStats hour2 =
        APIUsageStats.builder()
            .periodSeconds(3600)
            .minDelayTillNextRequestMillis(100)
            .remainingRequestsInPeriod(0.1f)
            .totalRequestsPerPeriod(900)
            .build();
    when(userThrottler.getStats(Mockito.eq(identifierString), Mockito.eq(ThrottleInterval.HOUR)))
        .thenReturn(Optional.of(hour2));
    apiThrottlingInterceptor.setUsageHeaderStats(req, resp, identifierString);
    assertEquals("3601", resp.getHeader("X-Rate-Limit-WaitTimeMillis"));
  }
}
