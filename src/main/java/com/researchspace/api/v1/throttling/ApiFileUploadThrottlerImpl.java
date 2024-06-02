package com.researchspace.api.v1.throttling;

import static java.lang.String.format;

import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.AbstractTokenBucketThrottler;
import com.researchspace.core.util.throttling.AllowanceTracker;
import com.researchspace.core.util.throttling.AllowanceTrackerSource;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.ThrottleLimitDefinition;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;

/**
 * Converted to Java from this SO post:
 * http://stackoverflow.com/questions/667508/whats-a-good-rate-limiting- algorithm
 */
@Slf4j
public class ApiFileUploadThrottlerImpl extends AbstractTokenBucketThrottler
    implements APIFileUploadThrottler {

  public ApiFileUploadThrottlerImpl(
      TimeSource timeSource,
      ThrottleDefinitionSet throttleLimitDefinitions,
      AllowanceTrackerSource allowanceSource) {
    super(timeSource, throttleLimitDefinitions, allowanceSource);
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
    Long interval = getIntervalSinceLastRequest(userTracker.getLastCheck(), current);
    doProceed(userTracker, current, interval, requestedResourceUnits);
    return proceed;
  }

  protected boolean isAllowed(
      Double requestedResourceUnits, double allowance, ThrottleLimitDefinition value) {
    // if we have a full allowance, we should allow a single file to be uploaded that is larger than
    // the limit / time,
    // so that large fields can be updated.
    if (Math.abs(allowance - value.getLimit()) < 0.01) {
      return true;
    } else {
      return allowance - requestedResourceUnits > 0;
    }
  }

  @Override
  public Optional<APIFileUploadStats> getStats(String id, ThrottleInterval throttleInterval) {
    AllowanceTracker userTracker = getAllowanceTrackerById(id);
    ThrottleLimitDefinition def = throttleLimitDefinitions.getThrottleDefinition(throttleInterval);
    if (def == null) {
      return Optional.empty();
    }
    double allowance = userTracker.getAllowance(throttleInterval);
    APIFileUploadStats stats = new APIFileUploadStats(def.getLimit(), allowance);
    return Optional.of(stats);
  }

  @Override
  public String getName() {
    return "TokenBucket";
  }

  public String toString() {
    return getName();
  }

  protected String getThrottleLimitExceededMessage(
      Map.Entry<ThrottleInterval, ThrottleLimitDefinition> pair) {
    String msg =
        format(
            "File upload limit exceeded: please restrict upload to no more than %d %s per %.0f "
                + " seconds on average",
            pair.getValue().getLimit(), pair.getValue().getUnits(), pair.getValue().getPer());
    return msg;
  }

  @Override
  protected void throwThrottleException(String msg) {
    throw new FileUploadLimitExceededException(msg);
  }
}
