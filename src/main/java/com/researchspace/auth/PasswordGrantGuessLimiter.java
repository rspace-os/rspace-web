package com.researchspace.auth;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.researchspace.auth.password.SentinelPasswordCheck;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Counts wrong password guesses per username on the OAuth password grant and refuses a username
 * once it reaches the limit. Held in memory per JVM and never persisted, so it resets on restart.
 * Only the OAuth password grant reads it; the web login form's lockout is separate and unchanged.
 *
 * <p>Each attempt is counted before its password check and a correct password clears the count.
 * Refused attempts are not counted, so a block ends {@code window} after the last admitted attempt.
 */
public class PasswordGrantGuessLimiter {

  private final int maxFailures;
  private final Cache<String, Integer> failures;
  private final SentinelPasswordCheck sentinelCheck;

  public PasswordGrantGuessLimiter(
      int maxFailures,
      Duration window,
      long maxTrackedAccounts,
      SentinelPasswordCheck sentinelCheck) {
    this(maxFailures, window, maxTrackedAccounts, Ticker.systemTicker(), sentinelCheck);
  }

  public PasswordGrantGuessLimiter(
      int maxFailures,
      Duration window,
      long maxTrackedAccounts,
      Ticker ticker,
      SentinelPasswordCheck sentinelCheck) {
    if (maxFailures < 1) {
      throw new IllegalArgumentException("oauth.passwordGrant.maxFailures must be at least 1");
    }
    if (window.compareTo(Duration.ofSeconds(1)) < 0) {
      throw new IllegalArgumentException(
          "oauth.passwordGrant.failureWindowSeconds must be at least 1");
    }
    this.maxFailures = maxFailures;
    this.sentinelCheck = sentinelCheck;
    this.failures =
        CacheBuilder.newBuilder()
            .concurrencyLevel(1)
            .expireAfterWrite(window.toNanos(), TimeUnit.NANOSECONDS)
            .maximumSize(maxTrackedAccounts)
            .ticker(ticker)
            .build();
  }

  /**
   * Counts one password attempt for {@code username} in a single atomic step, before the password
   * is checked, so overlapping requests cannot exceed the limit. Returns false, without counting,
   * once the limit is reached.
   */
  public boolean tryAcquire(String username) {
    Integer current = failures.getIfPresent(username);
    if (current != null && current >= maxFailures) {
      return false;
    }
    boolean[] admitted = {false};
    failures
        .asMap()
        .compute(
            username,
            (key, count) -> {
              if (count != null && count >= maxFailures) {
                return count;
              }
              admitted[0] = true;
              return count == null ? 1 : count + 1;
            });
    return admitted[0];
  }

  /**
   * Gives back the attempt counted for {@code username} when its password was never checked because
   * the verifier was busy, so a busy moment does not use up a guess.
   */
  public void uncount(String username) {
    failures.asMap().computeIfPresent(username, (key, count) -> count > 1 ? count - 1 : null);
  }

  /** Clears the count for {@code username} after a correct password. */
  public void recordSuccess(String username) {
    failures.invalidate(username);
  }

  /**
   * Runs one password check against the shared sentinel hash, queued on the submitted username like
   * a real check, so a request for an unknown username, or a refused one, costs the same as a wrong
   * password for a real one.
   */
  public void padWithSentinelCheck(String username, CharSequence suppliedPassword) {
    sentinelCheck.pad(username, suppliedPassword);
  }
}
