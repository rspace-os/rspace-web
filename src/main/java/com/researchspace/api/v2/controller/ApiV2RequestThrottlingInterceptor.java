package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.throttling.APIRequestThrottler;
import com.researchspace.api.v2.throttling.APIUsageStats;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Throttles API request rate per user. */
public class ApiV2RequestThrottlingInterceptor extends ApiV2AbstractThrottleInterceptor {

  static final String GLOBAL_ALLOWANCE_KEY = "global-id";
  private final APIRequestThrottler userThrottler;
  private final APIRequestThrottler globalThrottler;

  Logger log = LoggerFactory.getLogger(ApiV2RequestThrottlingInterceptor.class);

  public ApiV2RequestThrottlingInterceptor(
      APIRequestThrottler userThrottler, APIRequestThrottler globalThrottler) {
    Validate.noNullElements(
        new Object[] {userThrottler, globalThrottler}, "Throttlers cannot be null");
    this.userThrottler = userThrottler;
    this.globalThrottler = globalThrottler;
    if (globalThrottler.getMinIntervalMillis() > userThrottler.getMinIntervalMillis()) {
      log.warn(
          "Global minimum API request interval [{}] is greater than the per-user API minimum"
              + " request interval [{}].. this is probably a misconfiguration.",
          globalThrottler.getMinIntervalMillis(),
          userThrottler.getMinIntervalMillis());
    }
  }

  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // Anonymous public endpoints have already passed the IP-keyed pre-authentication limiter. They
    // must not consume the capacity reserved for successfully authenticated callers.
    if (request.getAttribute("user") == null) {
      return true;
    }
    String identifier = assertApiAccess(request);
    // Preserve v1 semantics: these headers describe the allowance before this request is consumed.
    setUsageHeaderStats(request, response, identifier);
    // Both buckets must be evaluated. Short-circuiting here lets a caller with an exhausted
    // per-client bucket generate unlimited rejection work outside the global admission ceiling.
    boolean clientAllowed = false;
    TooManyRequestsException clientFailure = null;
    try {
      clientAllowed = userThrottler.proceed(identifier);
    } catch (TooManyRequestsException ex) {
      clientFailure = ex;
    }
    boolean globallyAllowed;
    try {
      globallyAllowed = globalThrottler.proceed(GLOBAL_ALLOWANCE_KEY);
    } catch (TooManyRequestsException globalFailure) {
      if (clientFailure != null) {
        clientFailure.addSuppressed(globalFailure);
        throw clientFailure;
      }
      throw globalFailure;
    }
    if (clientFailure != null) {
      throw clientFailure;
    }
    return clientAllowed && globallyAllowed;
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
      // Expected, not a misconfiguration, when the throttler is APIRequestThrottler.PASS_THRU
      // (api.throttling.enabled=false).
      log.debug(
          "Could not obtain API usage stats for throttle with interval {} - is this configured?",
          ThrottleInterval.QUARTER_MIN.name());
    }
    if (optionalHourStats.isPresent()) {
      setHeaders(
          response, optionalHourStats.get(), ThrottleInterval.HOUR, minTimesIntegersCollector);
    } else {
      log.debug(
          "Could not obtain API usage stats for throttle with interval {} - is this configured?",
          ThrottleInterval.HOUR.name());
    }
    if (optionalDayStats.isPresent()) {
      setHeaders(response, optionalDayStats.get(), ThrottleInterval.DAY, minTimesIntegersCollector);
    } else {
      log.debug(
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
