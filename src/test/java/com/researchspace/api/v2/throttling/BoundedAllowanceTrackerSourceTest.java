package com.researchspace.api.v2.throttling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.researchspace.core.util.DefaultTimeSource;
import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedAllowanceTrackerSourceTest {

  private static ThrottleDefinitionSet definitions(int limitPerQuarterMinute) {
    ThrottleDefinitionSet definitions = new ThrottleDefinitionSet("test");
    definitions.addDefinition(ThrottleInterval.QUARTER_MIN, limitPerQuarterMinute);
    return definitions;
  }

  @Test
  void returnsTheSameTrackerInstancePerKeySoPerKeyLockingIsMeaningful() {
    BoundedAllowanceTrackerSource source =
        new BoundedAllowanceTrackerSource(new DefaultTimeSource(), definitions(15));

    assertSame(source.getAllowance("alice"), source.getAllowance("alice"));
    assertEquals(1, source.size());
  }

  @Test
  void evictsLeastRecentlyUsedKeysInsteadOfGrowingWithoutBound() {
    BoundedAllowanceTrackerSource source =
        new BoundedAllowanceTrackerSource(new DefaultTimeSource(), definitions(15), 4);

    for (int i = 0; i < 500; i++) {
      source.getAllowance("attacker-key-" + i);
    }

    assertEquals(4, source.size());
  }

  @Test
  void trackersFromTheBoundedSourceStillEnforceTheConfiguredLimit() {
    // The library uses (allowance - units) > 0, so back-to-back calls allow at most limit - 1.
    int limit = 3;
    TimeSource timeSource = new DefaultTimeSource();
    ThrottleDefinitionSet definitions = definitions(limit);
    ApiRequestThrottlerImpl throttler =
        new ApiRequestThrottlerImpl(
            timeSource, definitions, new BoundedAllowanceTrackerSource(timeSource, definitions));
    throttler.setMinIntervalMillis(0);

    int allowed = 0;
    try {
      for (int i = 0; i < limit + 5; i++) {
        throttler.proceed("alice");
        allowed++;
      }
      fail("expected the throttler to reject once the bucket emptied");
    } catch (TooManyRequestsException expected) {
      // The bucket ran out, which is the point of the test.
    }

    assertTrue(allowed > 0, "throttler rejected the very first request");
    assertTrue(allowed <= limit, "allowed " + allowed + " requests for a limit of " + limit);
  }

  @Test
  void concurrentRequestsForOneKeyCannotOverspendTheAllowance() throws Exception {
    TimeSource timeSource = new DefaultTimeSource();
    int limit = 5;
    ThrottleDefinitionSet definitions = definitions(limit);
    ApiRequestThrottlerImpl throttler =
        new ApiRequestThrottlerImpl(
            timeSource, definitions, new BoundedAllowanceTrackerSource(timeSource, definitions));
    throttler.setMinIntervalMillis(0);

    int threads = 32;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger allowed = new AtomicInteger();
    try {
      for (int i = 0; i < threads; i++) {
        pool.submit(
            () -> {
              start.await();
              try {
                if (throttler.proceed("alice")) {
                  allowed.incrementAndGet();
                }
              } catch (TooManyRequestsException expected) {
                // Rejected once the bucket is empty.
              }
              return null;
            });
      }
      start.countDown();
      pool.shutdown();
      assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }

    // Verified as a real check: with the per-key lock removed from proceed(), this reproducibly
    // allowed 11-13 of the 32 requests through against a limit of 5.
    assertTrue(
        allowed.get() <= limit, "allowed " + allowed.get() + " requests for a limit of " + limit);
  }
}
