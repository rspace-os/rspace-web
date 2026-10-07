package com.researchspace.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.base.Ticker;
import java.time.Duration;
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

  private void fail(String username, int times) {
    for (int i = 0; i < times; i++) {
      limiter.recordFailure(username);
    }
  }

  @Test
  public void blocksOnlyAtTheFailureLimit() {
    fail("bob", 4);
    assertFalse(limiter.isBlocked("bob"));
    fail("bob", 1);
    assertTrue(limiter.isBlocked("bob"));
  }

  @Test
  public void successClearsTheCount() {
    fail("bob", 4);
    limiter.recordSuccess("bob");
    fail("bob", 4);
    assertFalse(limiter.isBlocked("bob"));
  }

  @Test
  public void blockEndsAWindowAfterTheLastCountedFailure() {
    fail("bob", 4);
    ticker.advance(Duration.ofMinutes(9));
    fail("bob", 1);
    ticker.advance(Duration.ofMinutes(9));
    assertTrue(limiter.isBlocked("bob"));
    ticker.advance(Duration.ofMinutes(2));
    assertFalse(limiter.isBlocked("bob"));
  }

  @Test
  public void capEvictsTheOldestEntry() {
    fail("a", 5);
    ticker.advance(Duration.ofSeconds(1));
    fail("b", 5);
    fail("c", 5);
    fail("d", 5);
    assertFalse(limiter.isBlocked("a"));
    assertTrue(limiter.isBlocked("d"));
  }

  @Test
  public void padUnknownUserRunsOneCheckAgainstTheSentinel() {
    limiter.padUnknownUser("guess");
    verify(matcher, times(1))
        .test(argThat(u -> "oauth-sentinel".equals(u.getUsername())), eq("guess"));
  }
}
