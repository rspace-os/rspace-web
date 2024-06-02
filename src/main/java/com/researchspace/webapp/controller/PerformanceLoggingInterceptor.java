package com.researchspace.webapp.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Writes details of slow requests to a log file. This is set in log4j2.xml to be 'SlowRequests.txt'
 *
 * <p>Note: If rennaming or moving to another package, log4j configuration files should also be
 * updated.
 */
public class PerformanceLoggingInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(PerformanceLoggingInterceptor.class);

  private Integer thresholdMillis;
  private static final String REQUEST_START = "REQ-start";

  /**
   * A threshold in millis. Requests slower than the threshold will be logged.
   *
   * @param thresholdMillis
   */
  public PerformanceLoggingInterceptor(Integer thresholdMillis) {
    this.thresholdMillis = thresholdMillis;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
      throws Exception {
    request.setAttribute(REQUEST_START, getCurrentTimeMillis());
    return true;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      ModelAndView modelAndView)
      throws Exception {
    long end = getCurrentTimeMillis();
    long start = (long) request.getAttribute(REQUEST_START);
    long elapsedTime = end - start;
    if (elapsedTime > thresholdMillis) {
      String url = request.getRequestURI();
      writeToSlowLog(elapsedTime, request, url);
    }
  }

  // package scoped for testing
  long getCurrentTimeMillis() {
    return System.currentTimeMillis();
  }

  // package scoped for testing
  void writeToSlowLog(long elapsedTime, HttpServletRequest req, String url) {
    log.warn(
        "It took [{}] ms to process request to [{}] made by: [{}]",
        elapsedTime,
        url,
        req.getRemoteUser());
  }
}
