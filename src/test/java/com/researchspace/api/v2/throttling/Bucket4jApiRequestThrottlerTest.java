package com.researchspace.api.v2.throttling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.TimeSource;
import com.researchspace.core.util.throttling.ThrottleDefinitionSet;
import com.researchspace.core.util.throttling.ThrottleInterval;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

class Bucket4jApiRequestThrottlerTest {

  private final AtomicLong nowMillis = new AtomicLong();
  private final TimeSource timeSource = () -> new DateTime(nowMillis.get());

  @Test
  void enforcesEveryConfiguredWindowAndReportsItsRemainingAllowance() {
    Bucket4jApiRequestThrottler throttler = throttler(3, 2, 10, 0, 10_000);

    assertTrue(throttler.proceed("alice"));
    assertTrue(throttler.proceed("alice"));
    assertThrows(TooManyRequestsException.class, () -> throttler.proceed("alice"));
    assertEquals(
        1.0f,
        throttler
            .getStats("alice", ThrottleInterval.QUARTER_MIN)
            .orElseThrow()
            .getRemainingRequestsInPeriod());
    assertEquals(
        0.0f,
        throttler
            .getStats("alice", ThrottleInterval.HOUR)
            .orElseThrow()
            .getRemainingRequestsInPeriod());
  }

  @Test
  void refillsGreedily() {
    Bucket4jApiRequestThrottler throttler = throttler(3, null, null, 0, 10_000);
    throttler.proceed("alice");
    throttler.proceed("alice");
    throttler.proceed("alice");
    assertThrows(TooManyRequestsException.class, () -> throttler.proceed("alice"));

    nowMillis.addAndGet(5_000);

    assertTrue(throttler.proceed("alice"));
  }

  @Test
  void enforcesTheMinimumIntervalAsAnotherBandwidth() {
    Bucket4jApiRequestThrottler throttler = throttler(100, null, null, 100, 10_000);
    assertTrue(throttler.proceed("alice"));
    assertThrows(TooManyRequestsException.class, () -> throttler.proceed("alice"));

    nowMillis.addAndGet(100);

    assertTrue(throttler.proceed("alice"));
  }

  @Test
  void evictsIdentifiersInsteadOfGrowingWithoutBound() {
    Bucket4jApiRequestThrottler throttler = throttler(15, null, null, 0, 4);

    for (int i = 0; i < 500; i++) {
      throttler.proceed("attacker-key-" + i);
    }

    assertEquals(4, throttler.trackedIdentifierCount());
  }

  @Test
  void concurrentRequestsCannotOverspendAnAllowance() throws Exception {
    int limit = 5;
    Bucket4jApiRequestThrottler throttler = throttler(limit, null, null, 0, 10_000);
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
                throttler.proceed("alice");
                allowed.incrementAndGet();
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

    assertEquals(limit, allowed.get());
  }

  private Bucket4jApiRequestThrottler throttler(
      Integer quarterMinute,
      Integer hour,
      Integer day,
      int minimumInterval,
      int maxTrackedIdentifiers) {
    ThrottleDefinitionSet definitions = new ThrottleDefinitionSet("test");
    addDefinition(definitions, ThrottleInterval.QUARTER_MIN, quarterMinute);
    addDefinition(definitions, ThrottleInterval.HOUR, hour);
    addDefinition(definitions, ThrottleInterval.DAY, day);
    return new Bucket4jApiRequestThrottler(
        timeSource, definitions, minimumInterval, maxTrackedIdentifiers);
  }

  private static void addDefinition(
      ThrottleDefinitionSet definitions, ThrottleInterval interval, Integer limit) {
    if (limit != null) {
      definitions.addDefinition(interval, limit);
    }
  }
}
