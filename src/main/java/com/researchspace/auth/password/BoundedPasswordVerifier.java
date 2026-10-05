package com.researchspace.auth.password;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.lang3.Validate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Runs Argon2 login-password checks (Shiro login and default-realm reauthentication) through one
 * shared pool of permits, so the per-check heap allocation is bounded however many requests arrive
 * (ADR 0011). A username has at most one check in flight, so one account cannot hold more than one
 * permit, and after a wrong guess the username stays held for a configured delay, so one account
 * gets at most one guess per delay. Encoding new passwords is not bounded.
 */
public class BoundedPasswordVerifier {

  private final PasswordEncoder encoder;
  private final Semaphore permits;
  private final int maxPermits;
  private final long waitNanos;
  private final long failureDelayNanos;
  private final ConcurrentHashMap<String, PrincipalLock> principalLocks = new ConcurrentHashMap<>();

  public BoundedPasswordVerifier(
      PasswordEncoder encoder, int permits, Duration wait, Duration failureDelay) {
    Validate.notNull(encoder);
    Validate.isTrue(permits >= 1, "Password verification needs at least 1 permit, got %d", permits);
    Validate.isTrue(!wait.isNegative(), "Password verification wait must not be negative");
    Validate.isTrue(
        !failureDelay.isNegative(), "Password verification failure delay must not be negative");
    this.encoder = encoder;
    this.maxPermits = permits;
    this.permits = new Semaphore(permits, true);
    this.waitNanos = wait.toNanos();
    this.failureDelayNanos = failureDelay.toNanos();
  }

  /**
   * Checks a password, waiting for the username's turn and then for a free permit, together bounded
   * by the configured wait. A mismatch returns only after the failure delay, during which the
   * username stays held but its permit is back in the pool.
   *
   * @return whether the password matched
   * @throws LoginVerificationBusyException if the wait elapses first
   * @throws IllegalArgumentException if the stored value has no recognised encoding
   */
  public boolean verify(String username, CharSequence rawPassword, String encodedPassword) {
    long deadline = System.nanoTime() + waitNanos;
    PrincipalLock principalLock = acquireHolder(username);
    try {
      if (!principalLock.lock.tryLock(remaining(deadline), TimeUnit.NANOSECONDS)) {
        throw busy(username, "another check for this username is still running");
      }
      try {
        boolean matches = matchesWithPermit(username, rawPassword, encodedPassword, deadline);
        if (!matches) {
          holdForFailureDelay();
        }
        return matches;
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

  private boolean matchesWithPermit(
      String username, CharSequence rawPassword, String encodedPassword, long deadline)
      throws InterruptedException {
    if (!permits.tryAcquire(remaining(deadline), TimeUnit.NANOSECONDS)) {
      throw busy(username, "all " + maxPermits + " verification permits are in use");
    }
    try {
      return encoder.matches(rawPassword, encodedPassword);
    } finally {
      permits.release();
    }
  }

  private void holdForFailureDelay() {
    try {
      TimeUnit.NANOSECONDS.sleep(failureDelayNanos);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
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
