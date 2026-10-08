package com.researchspace.auth;

import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.model.User;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.lang.util.ByteSource;

/**
 * Counts wrong password guesses per username on the OAuth password grant and refuses a username
 * once it reaches the limit. Held in memory per JVM and never persisted, so it resets on restart.
 * Only the OAuth password grant reads it; the web login form's lockout is separate and unchanged.
 * Only usernames that resolve to a real account are counted (the controller calls tryAcquire after
 * the user lookup), so the map holds at most one entry per account.
 *
 * <p>Each attempt is counted before its password check and a correct password clears the count.
 * Refused attempts are not counted, so a block ends {@code window} after the last admitted attempt.
 */
public class PasswordGrantGuessLimiter {

  private final int maxFailures;
  private final Cache<String, Integer> failures;
  private final UsernamePasswordCredentialsMatcher credentialsMatcher;
  private final User sentinel;

  public PasswordGrantGuessLimiter(
      int maxFailures,
      Duration window,
      long maxTrackedAccounts,
      UsernamePasswordCredentialsMatcher credentialsMatcher) {
    this(maxFailures, window, maxTrackedAccounts, Ticker.systemTicker(), credentialsMatcher);
  }

  public PasswordGrantGuessLimiter(
      int maxFailures,
      Duration window,
      long maxTrackedAccounts,
      Ticker ticker,
      UsernamePasswordCredentialsMatcher credentialsMatcher) {
    this.maxFailures = maxFailures;
    this.credentialsMatcher = credentialsMatcher;
    this.sentinel = createSentinel();
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

  /** Clears the count for {@code username} after a correct password. */
  public void recordSuccess(String username) {
    failures.invalidate(username);
  }

  /**
   * Runs one password check against a never-persisted sentinel user, so a request for an unknown
   * username, or a refused one, costs the same as a wrong password for a real one.
   */
  public void padWithSentinelCheck(String suppliedPassword) {
    credentialsMatcher.test(sentinel, suppliedPassword);
  }

  private static User createSentinel() {
    SecureRandomNumberGenerator random = new SecureRandomNumberGenerator();
    ByteSource salt = random.nextBytes(16);
    User user = new User("oauth-sentinel");
    user.setPassword(CryptoUtils.hashWithSha256inHex(random.nextBytes(32).toHex(), salt));
    user.setSalt(salt.toBase64());
    return user;
  }
}
