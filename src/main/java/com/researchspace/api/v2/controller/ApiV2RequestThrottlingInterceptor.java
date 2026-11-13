package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.throttling.APIRequestThrottler;
import com.researchspace.api.v2.throttling.APIUsageStats;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Throttles authenticated API requests by originating actor. */
public class ApiV2RequestThrottlingInterceptor extends ApiV2AbstractThrottleInterceptor {

  static final String GLOBAL_ALLOWANCE_KEY = "global-id";
  private final APIRequestThrottler userThrottler;
  private final APIRequestThrottler globalThrottler;

  private static final Logger log =
      LoggerFactory.getLogger(ApiV2RequestThrottlingInterceptor.class);

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
    if (ApiV2Caller.from(request) == null) {
      return true;
    }
    String identifier = throttleKey(request);
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

  /**
   * Sets {@code X-Rate-Limit-Limit-<interval>}, {@code X-Rate-Limit-Remaining-<interval>} and
   * {@code X-Rate-Limit-WaitTimeTillNextRequestMillis-<interval>} for each configured interval,
   * plus the longest wait and minimum interval across them. Set before the throttle check so a 429
   * carries them too.
   */
  void setUsageHeaderStats(
      HttpServletRequest request, HttpServletResponse response, String identifier) {
    int waitTime = -1;
    int minWaitInterval = -1;
    for (ThrottleInterval interval : ThrottleInterval.values()) {
      Optional<APIUsageStats> optionalStats = userThrottler.getStats(identifier, interval);
      if (optionalStats.isEmpty()) {
        // Expected, not a misconfiguration, when APIRequestThrottler.PASS_THRU is configured.
        log.debug(
            "Could not obtain API usage stats for throttle with interval {} - is this configured?",
            interval.name());
        continue;
      }
      APIUsageStats stats = optionalStats.orElseThrow();
      String suffix = "-" + interval.name().toLowerCase(Locale.ROOT);
      response.setHeader(
          "X-Rate-Limit-Limit" + suffix, Integer.toString(stats.getTotalRequestsPerPeriod()));
      response.setHeader(
          "X-Rate-Limit-Remaining" + suffix,
          Long.toString((long) Math.floor(stats.getRemainingRequestsInPeriod())));
      response.setHeader(
          "X-Rate-Limit-WaitTimeTillNextRequestMillis" + suffix,
          Integer.toString(stats.millisDelayTillNextRequest()));
      waitTime = Math.max(waitTime, stats.millisDelayTillNextRequest());
      minWaitInterval = Math.max(minWaitInterval, stats.getMinDelayTillNextRequestMillis());
    }
    if (waitTime >= 0) {
      response.setHeader("X-Rate-Limit-WaitTimeMillis", Integer.toString(waitTime));
      response.setHeader("X-Rate-Limit-MinWaitIntervalMillis", Integer.toString(minWaitInterval));
    }
  }
}
