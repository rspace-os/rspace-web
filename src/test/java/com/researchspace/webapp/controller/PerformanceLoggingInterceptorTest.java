package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PerformanceLoggingInterceptorTest {

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void before() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  class PerformanceLoggingInteceptorTSS extends PerformanceLoggingInterceptor {
    int loggedCount = 0;
    long currTime = 0;

    public PerformanceLoggingInteceptorTSS(Integer thresholdMillis) {
      super(thresholdMillis);
    }

    // record that log was written to
    void writeToSlowLog(long elapsedTime, HttpServletRequest req, String url) {
      loggedCount++;
    }

    // be able to specify time rather than depend on clock times
    long getCurrentTimeMillis() {
      return currTime;
    }
  }

  @Test
  void logsTriggeredByThreshold() throws Exception {
    final int threshold = 100;
    PerformanceLoggingInteceptorTSS interceptor = new PerformanceLoggingInteceptorTSS(threshold);
    assertEquals(0, interceptor.loggedCount);
    interceptor.preHandle(request, response, null);

    for (Integer time : List.of(threshold - 1, threshold)) {
      interceptor.currTime = time;
      interceptor.postHandle(request, response, null, null);
      assertEquals(0, interceptor.loggedCount, "threshold " + time + "shouldn't be logged");
    }

    // threshold exceeded, logged.
    interceptor.currTime = threshold + 1;
    interceptor.postHandle(request, response, null, null);
    assertEquals(1, interceptor.loggedCount);
  }
}
