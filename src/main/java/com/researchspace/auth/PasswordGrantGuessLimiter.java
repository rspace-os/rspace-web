package com.researchspace.auth;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Counts wrong password guesses per username on the OAuth password grant and refuses a username
 * once it reaches the limit. Held in memory per JVM and never persisted, so it resets on restart.
 * Only the OAuth password grant reads it; the web login form's lockout is separate and unchanged.
 *
 * <p>Refused attempts are not recorded, so a block ends {@code window} after the last counted
 * failure and cannot be extended by repeating refused requests.
 */
public class PasswordGrantGuessLimiter {

  private final int maxFailures;
  private final Cache<String, Integer> failures;

  public PasswordGrantGuessLimiter(int maxFailures, Duration window, long maxTrackedAccounts) {
    this(maxFailures, window, maxTrackedAccounts, Ticker.systemTicker());
  }

  public PasswordGrantGuessLimiter(
      int maxFailures, Duration window, long maxTrackedAccounts, Ticker ticker) {
    this.maxFailures = maxFailures;
    this.failures =
        CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .expireAfterWrite(window.toNanos(), TimeUnit.NANOSECONDS)
            .maximumSize(maxTrackedAccounts)
            .ticker(ticker)
            .build();
  }

  /** Whether {@code username} has reached the failure limit inside the current window. */
  public boolean isBlocked(String username) {
    Integer count = failures.getIfPresent(username);
    return count != null && count >= maxFailures;
  }

  /** Counts one wrong password for {@code username}, restarting its window. */
  public void recordFailure(String username) {
    failures.asMap().merge(username, 1, Integer::sum);
  }

  /** Clears the count for {@code username} after a correct password. */
  public void recordSuccess(String username) {
    failures.invalidate(username);
  }
}
