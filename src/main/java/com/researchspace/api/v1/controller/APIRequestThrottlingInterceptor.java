package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.throttling.APIRequestThrottler;
import com.researchspace.api.v1.throttling.APIUsageStats;
import com.researchspace.core.util.throttling.ThrottleInterval;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Throttles API request rate per user. */
public class APIRequestThrottlingInterceptor extends AbstractThrottleInterceptor {

  void setInventoryThrottler(APIRequestThrottler inventoryThrottler) {
    this.inventoryThrottler = inventoryThrottler;
  }

  static final String GLOBAL_ALLOWANCE_KEY = "global-id";
  private APIRequestThrottler userThrottler;
  private APIRequestThrottler globalThrottler;
  private APIRequestThrottler inventoryThrottler;

  void setUserThrottler(APIRequestThrottler userThrottler) {
    this.userThrottler = userThrottler;
  }

  void setGlobalThrottler(APIRequestThrottler globalThrottler) {
    this.globalThrottler = globalThrottler;
  }

  Logger log = LoggerFactory.getLogger(APIRequestThrottlingInterceptor.class);

  public APIRequestThrottlingInterceptor(
      @Autowired APIRequestThrottler userThrottler,
      @Autowired APIRequestThrottler globalThrottler,
      @Autowired APIRequestThrottler inventoryThrottler) {
    Validate.noNullElements(
        new Object[] {userThrottler, globalThrottler, inventoryThrottler},
        "Throttlers cannot be null");
    this.userThrottler = userThrottler;
    this.globalThrottler = globalThrottler;
    this.inventoryThrottler = inventoryThrottler;
    if (globalThrottler.getMinIntervalMillis() > userThrottler.getMinIntervalMillis()) {
      log.warn(
          "Global minimum API request interval [{}] is greater than the per-user API minimum"
              + " request interval [{}].. this is probably a misconfiguration.",
          globalThrottler.getMinIntervalMillis(),
          userThrottler.getMinIntervalMillis());
    }
  }

  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    String identifier = assertApiAccess(request);
    if (isInventoryRequest(request)) {
      setInvHeaderStats(request, response, identifier);
      return inventoryThrottler.proceed(identifier);
    } else {
      setUsageHeaderStats(request, response, identifier);
      return userThrottler.proceed(identifier) && globalThrottler.proceed(GLOBAL_ALLOWANCE_KEY);
    }
  }

  private void setInvHeaderStats(
      HttpServletRequest request, HttpServletResponse response, String identifier) {
    Optional<APIUsageStats> optionalHourStats =
        inventoryThrottler.getStats(identifier, ThrottleInterval.HOUR);
    if (optionalHourStats.isPresent()) {
      APIUsageStats stats = optionalHourStats.get();
      response.setHeader(
          "X-Rate-Limit-Limit-Inv" + "-" + ThrottleInterval.HOUR.name().toLowerCase(),
          stats.getTotalRequestsPerPeriod() + "");
      response.setHeader(
          "X-Rate-Limit-Remaining-Inv" + "-" + ThrottleInterval.HOUR.name().toLowerCase(),
          stats.getRemainingRequestsInPeriod() + "");
      response.setHeader(
          "X-Rate-Limit-WaitTimeTillNextRequestMillis-Inv"
              + "-"
              + ThrottleInterval.HOUR.name().toLowerCase(),
          stats.millisDelayTillNextRequest() + "");
      response.setHeader("X-Rate-Limit-WaitTimeMillis", stats.millisDelayTillNextRequest() + "");
    } else {
      log.warn("Couldn't get inventory api usage stats for {}", identifier);
    }
  }

  private boolean isInventoryRequest(HttpServletRequest request) {
    log.info("request URI is {}", request.getRequestURI());
    return request.getRequestURL().toString().contains("inventory/v1");
  }

  void setUsageHeaderStats(
      HttpServletRequest request, HttpServletResponse response, String identifier) {
    // we update the headers here, before testing throttler, in case exception is
    // thrown.
    // This still counts as an API call even if it fails.
    Optional<APIUsageStats> optional15sStats =
        userThrottler.getStats(identifier, ThrottleInterval.QUARTER_MIN);
    Optional<APIUsageStats> optionalHourStats =
        userThrottler.getStats(identifier, ThrottleInterval.HOUR);
    Optional<APIUsageStats> optionalDayStats =
        userThrottler.getStats(identifier, ThrottleInterval.DAY);
    List<Integer> minTimesIntegersCollector = new ArrayList<Integer>();
    if (optional15sStats.isPresent()) {
      setHeaders(
          response,
          optional15sStats.get(),
          ThrottleInterval.QUARTER_MIN,
          minTimesIntegersCollector);
    } else {
      log.warn(
          "Could not obtain API usage stats for throttle with interval {} - is this configured?",
          ThrottleInterval.QUARTER_MIN.name());
    }
    if (optionalHourStats.isPresent()) {
      setHeaders(
          response, optionalHourStats.get(), ThrottleInterval.HOUR, minTimesIntegersCollector);
    } else {
      log.warn(
          "Could not obtain API usage stats for throttle with interval {} - is this configured?",
          ThrottleInterval.HOUR.name());
    }
    if (optionalDayStats.isPresent()) {
      setHeaders(response, optionalDayStats.get(), ThrottleInterval.DAY, minTimesIntegersCollector);
    } else {
      log.warn(
          "Could not obtain API usage stats for throttle with interval {} - is this configured?",
          ThrottleInterval.DAY.name());
    }
    if (optional15sStats.isPresent()) {
      minTimesIntegersCollector.add(optional15sStats.get().getMinDelayTillNextRequestMillis());
      // this is the maximum of all the minimum wait times
      Integer waitTime = Collections.max(minTimesIntegersCollector);
      response.setHeader("X-Rate-Limit-WaitTimeMillis", waitTime + "");
    }
  }

  void setHeaders(
      HttpServletResponse response,
      APIUsageStats optionalStats,
      ThrottleInterval interval,
      List<Integer> minTimesIntegersCollector) {
    // for backwards naming compatibility
    if (ThrottleInterval.QUARTER_MIN.equals(interval)) {
      response.setHeader("X-Rate-Limit-Limit", optionalStats.getTotalRequestsPerPeriod() + "");
      response.setHeader(
          "X-Rate-Limit-Remaining", optionalStats.getRemainingRequestsInPeriod() + "");
      response.setHeader(
          "X-Rate-Limit-WaitTimeTillNextRequestMillis" + "-" + interval.name().toLowerCase(),
          optionalStats.millisDelayTillNextRequest() + "");
      minTimesIntegersCollector.add(optionalStats.millisDelayTillNextRequest());
    } else {
      // name headers based on interval
      response.setHeader(
          "X-Rate-Limit-Limit" + "-" + interval.name().toLowerCase(),
          optionalStats.getTotalRequestsPerPeriod() + "");
      response.setHeader(
          "X-Rate-Limit-Remaining" + "-" + interval.name().toLowerCase(),
          optionalStats.getRemainingRequestsInPeriod() + "");
      response.setHeader(
          "X-Rate-Limit-WaitTimeTillNextRequestMillis" + "-" + interval.name().toLowerCase(),
          optionalStats.millisDelayTillNextRequest() + "");
      minTimesIntegersCollector.add(optionalStats.millisDelayTillNextRequest());
    }
    response.setHeader(
        "X-Rate-Limit-MinWaitIntervalMillis",
        optionalStats.getMinDelayTillNextRequestMillis() + "");
  }
}
