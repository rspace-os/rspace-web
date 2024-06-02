package com.researchspace.api.v1.throttling;

import static java.lang.String.format;

import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.AbstractTokenBucketThrottler;
import com.researchspace.core.util.throttling.AllowanceTracker;
import com.researchspace.core.util.throttling.AllowanceTrackerSource;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.ThrottleLimitDefinition;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;

/**
 * Converted to Java from this SO post:
 * http://stackoverflow.com/questions/667508/whats-a-good-rate-limiting- algorithm
 */
@Slf4j
public class ApiRequestThrottlerImpl extends AbstractTokenBucketThrottler
    implements APIRequestThrottler {

  private static final String THROTTLE_LIMIT_EXCEEDED_FMT =
      "Too many requests: please restrict requests to no more than %d per %.0f  seconds on average";
  private static final String TOO_FREQUENT_REQUEST_FM =
      "Requests were too frequent - please allow at least %d ms between requests."
          + " Last interval between requests was only %d ms";

  public ApiRequestThrottlerImpl(
      TimeSource timeSource,
      ThrottleDefinitionSet throttleLimitDefinitions,
      AllowanceTrackerSource allowanceSource) {
    super(timeSource, throttleLimitDefinitions, allowanceSource);
  }

  public static final int DEFAULT_MIN_INTERVAL_MILLIS = 100;

  private int minIntervalMillis = DEFAULT_MIN_INTERVAL_MILLIS;

  public int getMinIntervalMillis() {
    return minIntervalMillis;
  }

  /**
   * Sets min rate interval. A min rate interval of 0 means there is no absolute restriction on rate
   * of requests
   *
   * @param minIntervalMillis A minimal interval between requests; must be &gt= 0
   * @throws IllegalArgumentException if <code>minIntervalMillis</code> &lt 0
   */
  public void setMinIntervalMillis(int minIntervalMillis) {
    Validate.isTrue(
        minIntervalMillis >= 0,
        format("minIntervalMillis must be >= 0 but was: [%d]", minIntervalMillis));
    this.minIntervalMillis = minIntervalMillis;
  }

  @Override
  public boolean proceed(String userId) {
    return this.proceed(userId, 1.0);
  }

  /*
   * (non-Javadoc)
   *
   * @see com.researchspace.api.v1.throttling.APIThrottler#proceed()
   */
  @Override
  public boolean proceed(String userId, Double requestedResourceUnits) {
    boolean proceed = true;
    AllowanceTracker userTracker = getAllowanceTrackerById(userId);
    DateTime current = timeSource.now();
    long interval = minIntervalMillis;
    // the first time this is called might be straight after
    // initialisation,
    // in which case interval will be very small, probably < minInterval.

    if (!userTracker.isFirstTime()) {
      interval = getIntervalSinceLastRequest(userTracker.getLastCheck(), current);
      rejectIfIntervalLessThanMinInterval(interval);
    }
    userTracker.setFirstTime(false);
    doProceed(userTracker, current, interval, requestedResourceUnits);
    return proceed;
  }

  private void rejectIfIntervalLessThanMinInterval(long interval) {
    if (interval < minIntervalMillis) {
      String msg = format(TOO_FREQUENT_REQUEST_FM, minIntervalMillis, interval);
      log.warn(msg);
      throw new TooManyRequestsException(msg);
    }
  }

  @Override
  public Optional<APIUsageStats> getStats(String id, ThrottleInterval throttleInterval) {
    AllowanceTracker userTracker = getAllowanceTrackerById(id);
    ThrottleLimitDefinition def = throttleLimitDefinitions.getThrottleDefinition(throttleInterval);
    if (def == null) {
      return Optional.empty();
    }

    APIUsageStats stats =
        APIUsageStats.builder()
            .minDelayTillNextRequestMillis(getMinIntervalMillis())
            .periodSeconds((int) def.getPer())
            .remainingRequestsInPeriod((float) userTracker.getAllowance(throttleInterval))
            .totalRequestsPerPeriod(def.getLimit())
            .build();
    return Optional.of(stats);
  }

  @Override
  public String getName() {
    return "TokenBucket";
  }

  public String toString() {
    return getName();
  }

  @Override
  protected String getThrottleLimitExceededMessage(
      Map.Entry<ThrottleInterval, ThrottleLimitDefinition> pair) {
    String msg =
        format(THROTTLE_LIMIT_EXCEEDED_FMT, pair.getValue().getLimit(), pair.getValue().getPer());
    return msg;
  }

  @Override
  protected void throwThrottleException(String msg) {
    throw new TooManyRequestsException(msg);
  }
}
