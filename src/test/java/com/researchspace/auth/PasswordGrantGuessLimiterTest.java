package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Ticker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PasswordGrantGuessLimiterTest {

  private static final Duration WINDOW = Duration.ofMinutes(10);

  private static class FakeTicker extends Ticker {
    private long nanos;

    @Override
    public long read() {
      return nanos;
    }

    void advance(Duration d) {
      nanos += d.toNanos();
    }
  }

  private FakeTicker ticker;
  private UsernamePasswordCredentialsMatcher matcher;
  private PasswordGrantGuessLimiter limiter;

  @BeforeEach
  public void setUp() {
    ticker = new FakeTicker();
    matcher = mock(UsernamePasswordCredentialsMatcher.class);
    limiter = new PasswordGrantGuessLimiter(5, WINDOW, 3, ticker, matcher);
  }

  private void acquire(String username, int times) {
    for (int i = 0; i < times; i++) {
      assertTrue(limiter.tryAcquire(username));
    }
  }

  @Test
  public void rejectsMaxFailuresBelowOne() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new PasswordGrantGuessLimiter(0, WINDOW, 3, ticker, matcher));
    assertEquals("oauth.passwordGrant.maxFailures must be at least 1", e.getMessage());
  }

  @Test
  public void rejectsWindowShorterThanOneSecond() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new PasswordGrantGuessLimiter(5, Duration.ZERO, 3, ticker, matcher));
    assertEquals("oauth.passwordGrant.failureWindowSeconds must be at least 1", e.getMessage());
  }

  @Test
  public void admitsUpToTheLimitThenRefuses() {
    acquire("bob", 5);
    assertFalse(limiter.tryAcquire("bob"));
  }

  @Test
  public void successClearsTheCount() {
    acquire("bob", 4);
    limiter.recordSuccess("bob");
    acquire("bob", 5);
  }

  @Test
  public void blockEndsAWindowAfterTheLastAdmittedAttempt() {
    acquire("bob", 4);
    ticker.advance(Duration.ofMinutes(9));
    acquire("bob", 1);
    ticker.advance(Duration.ofMinutes(9));
    assertFalse(limiter.tryAcquire("bob"));
    ticker.advance(Duration.ofMinutes(2));
    assertTrue(limiter.tryAcquire("bob"));
  }

  @Test
  public void capEvictsTheOldestEntry() {
    acquire("a", 5);
    ticker.advance(Duration.ofSeconds(1));
    acquire("b", 5);
    acquire("c", 5);
    acquire("d", 5);
    assertTrue(limiter.tryAcquire("a"));
    assertFalse(limiter.tryAcquire("d"));
  }

  @Test
  public void concurrentAttemptsAreAdmittedOnlyUpToTheLimit() throws Exception {
    acquire("bob", 4);
    int threads = 20;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return limiter.tryAcquire("bob");
                }));
      }
      start.countDown();
      int admitted = 0;
      for (Future<Boolean> result : results) {
        if (result.get(10, TimeUnit.SECONDS)) {
          admitted++;
        }
      }
      assertEquals(1, admitted);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void padWithSentinelCheckRunsOneCheckAgainstTheSentinel() {
    limiter.padWithSentinelCheck("guess");
    verify(matcher, times(1))
        .test(argThat(u -> "oauth-sentinel".equals(u.getUsername())), eq("guess"));
  }
}
