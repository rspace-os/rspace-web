package com.researchspace.api.v1.throttling;

import static org.junit.Assert.*;

import org.junit.Test;

public class APIUsageStatsTest {

  @Test
  public void testGetRemainingRequestInPeriodCount() {

    // 10 requests /10 sec == 1 second max to recover.
    // here we have to recover 0.9 allowance, will take 0.9s
    APIUsageStats stats =
        APIUsageStats.builder()
            .periodSeconds(10)
            .minDelayTillNextRequestMillis(1)
            .totalRequestsPerPeriod(10)
            .remainingRequestsInPeriod(0.1f)
            .build();

    assertEquals(901, stats.millisDelayTillNextRequest());

    // set our allowance to > 1. We should only have to wait the minimum interval
    // now
    stats =
        APIUsageStats.builder()
            .periodSeconds(10)
            .minDelayTillNextRequestMillis(1)
            .totalRequestsPerPeriod(10)
            .remainingRequestsInPeriod(1.1f)
            .build();
    assertEquals(1, stats.millisDelayTillNextRequest());

    // set 50 requests /10 seconds throttling should recover 5 x faster, i.e
    // in 180ms (0.9 units at 5units/s = 180 millis)
    stats =
        APIUsageStats.builder()
            .periodSeconds(10)
            .minDelayTillNextRequestMillis(1)
            .totalRequestsPerPeriod(50)
            .remainingRequestsInPeriod(0.1f)
            .build();
    assertEquals(181, stats.millisDelayTillNextRequest());

    // make recovery time (90ms) < absolute min interval(100). Check that the largest is returned
    stats =
        APIUsageStats.builder()
            .periodSeconds(10)
            .minDelayTillNextRequestMillis(100)
            .totalRequestsPerPeriod(100)
            .remainingRequestsInPeriod(0.1f)
            .build();
    assertEquals(100, stats.millisDelayTillNextRequest());
  }
}
