package com.researchspace.auth.password;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang3.Validate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Runs stored-password checks (Shiro login, default-realm reauthentication and SSO/Community
 * verification passwords) through one shared pool of permits, so the per-check heap allocation is
 * bounded however many requests arrive (ADR 0011). A username has at most one check in flight, so
 * one account cannot hold more than one permit. Encoding new passwords is not bounded.
 */
public class BoundedPasswordVerifier {

  /**
   * @param matches whether the password matched
   * @param upgradedHash the password re-encoded with the current default when it matched against an
   *     outdated encoding, otherwise null
   */
  public record Result(boolean matches, String upgradedHash) {
    public Result {
      Validate.isTrue(matches || upgradedHash == null, "A mismatch has no upgraded hash");
    }

    public static Result mismatch() {
      return new Result(false, null);
    }

    public static Result matched(String upgradedHash) {
      return new Result(true, upgradedHash);
    }
  }

  private final PasswordEncoder encoder;
  private final Semaphore permits;
  private final int maxPermits;
  private final long waitNanos;
  private final ConcurrentHashMap<String, PrincipalLock> principalLocks = new ConcurrentHashMap<>();

  public BoundedPasswordVerifier(PasswordEncoder encoder, int permits, Duration wait) {
    Validate.notNull(encoder);
    Validate.isTrue(permits >= 1, "Password verification needs at least 1 permit, got %d", permits);
    Validate.isTrue(!wait.isNegative(), "Password verification wait must not be negative");
    this.encoder = encoder;
    this.maxPermits = permits;
    this.permits = new Semaphore(permits, true);
    this.waitNanos = wait.toNanos();
  }

  /**
   * Checks a password, waiting for the username's turn and then for a free permit, together bounded
   * by the configured wait.
   *
   * @throws LoginVerificationBusyException if the wait elapses first
   * @throws IllegalArgumentException if the stored value has no recognised encoding
   */
  public Result verify(String username, CharSequence rawPassword, String encodedPassword) {
    long deadline = System.nanoTime() + waitNanos;
    PrincipalLock principalLock = acquireHolder(username);
    try {
      if (!principalLock.lock.tryLock(remaining(deadline), TimeUnit.NANOSECONDS)) {
        throw busy(username, "another check for this username is still running");
      }
      try {
        if (!permits.tryAcquire(remaining(deadline), TimeUnit.NANOSECONDS)) {
          throw busy(username, "all " + maxPermits + " verification permits are in use");
        }
        try {
          return check(rawPassword, encodedPassword);
        } finally {
          permits.release();
        }
      } finally {
        principalLock.lock.unlock();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw busy(username, "interrupted while waiting");
    } finally {
      releaseHolder(username);
    }
  }

  private Result check(CharSequence rawPassword, String encodedPassword) {
    if (!encoder.matches(rawPassword, encodedPassword)) {
      return Result.mismatch();
    }
    return Result.matched(
        encoder.upgradeEncoding(encodedPassword) ? encoder.encode(rawPassword) : null);
  }

  private PrincipalLock acquireHolder(String username) {
    return principalLocks.compute(
        username,
        (k, existing) -> {
          PrincipalLock lock = existing == null ? new PrincipalLock() : existing;
          lock.holders++;
          return lock;
        });
  }

  private void releaseHolder(String username) {
    principalLocks.computeIfPresent(username, (k, lock) -> --lock.holders == 0 ? null : lock);
  }

  private static long remaining(long deadline) {
    return Math.max(0, deadline - System.nanoTime());
  }

  private static LoginVerificationBusyException busy(String username, String reason) {
    return new LoginVerificationBusyException(
        "Password verification for [" + username + "] refused: " + reason);
  }

  /** Mutated only inside {@link ConcurrentHashMap#compute}, which serialises per key. */
  private static final class PrincipalLock {
    private final ReentrantLock lock = new ReentrantLock();
    private int holders;
  }

  int availablePermits() {
    return permits.availablePermits();
  }

  int trackedPrincipals() {
    return principalLocks.size();
  }
}
