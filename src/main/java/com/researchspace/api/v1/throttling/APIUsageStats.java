package com.researchspace.api.v1.throttling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

/** Read-only usage statistics */
@Value
@AllArgsConstructor
@Builder
public class APIUsageStats {

  /** Total requests allowed in period. This is configured by server policy */
  int totalRequestsPerPeriod;

  /** Remaining allowance in period */
  float remainingRequestsInPeriod;

  /** The absolute Min delay required before next API call, regardless of throttling */
  int minDelayTillNextRequestMillis;

  /** The time period over which we are throttling, in seconds. Usually 15s, 1hour or 1 day */
  int periodSeconds;

  /**
   * Derived value to indicate how long to wait before the next request can be made. This will
   * always return a value 1 more than calculated value to ensure that calculated allowance will now
   * not be throttled
   *
   * @return amount of time in millis before next request should be sent.
   */
  public int millisDelayTillNextRequest() {
    if (remainingRequestsInPeriod >= 1.0) {
      return minDelayTillNextRequestMillis;
    }
    // recovery time in millis
    int millisToRecover =
        Math.round(
                (1.0f - remainingRequestsInPeriod)
                    * (1.0f * periodSeconds / totalRequestsPerPeriod)
                    * 1000)
            + 1; // for rounding up ;
    return Math.max(millisToRecover, minDelayTillNextRequestMillis);
  }
}
