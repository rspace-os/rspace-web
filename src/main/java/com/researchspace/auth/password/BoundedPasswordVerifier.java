package com.researchspace.auth.password;

import java.time.Duration;
import java.util.concurrent.Callable;
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
 * permit. Encoding new passwords is not bounded.
 */
public class BoundedPasswordVerifier {

  private final PasswordEncoder encoder;
  private final Semaphore permits;
  private final int maxPermits;
  private final long waitNanos;
  private final ConcurrentHashMap<String, PrincipalLock> principalLocks = new ConcurrentHashMap<>();
  private final ThreadLocal<Long> sharedDeadline = new ThreadLocal<>();

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
   * Runs an action while holding the username's lock, the same lock {@link #verify} takes, so a
   * whole login attempt for one account runs one at a time. Waits up to the configured wait for the
   * lock, and a {@link #verify} on this thread inside the action shares that one wait.
   *
   * @return the action's result
   * @throws LoginVerificationBusyException if the wait elapses first
   */
  public <T> T runExclusive(String username, Callable<T> action) throws Exception {
    long deadline = System.nanoTime() + waitNanos;
    PrincipalLock principalLock = acquireHolder(username);
    try {
      try {
        if (!principalLock.lock.tryLock(remaining(deadline), TimeUnit.NANOSECONDS)) {
          throw busy(username, "another login for this username is still running");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw busy(username, "interrupted while waiting");
      }
      Long outerDeadline = sharedDeadline.get();
      sharedDeadline.set(deadline);
      try {
        return action.call();
      } finally {
        if (outerDeadline == null) {
          sharedDeadline.remove();
        } else {
          sharedDeadline.set(outerDeadline);
        }
        principalLock.lock.unlock();
      }
    } finally {
      releaseHolder(username);
    }
  }

  /**
   * Checks a password, waiting for the username's turn and then for a free permit, together bounded
   * by the configured wait.
   *
   * @return whether the password matched
   * @throws LoginVerificationBusyException if the wait elapses first
   * @throws IllegalArgumentException if the stored value has no recognised encoding
   */
  public boolean verify(String username, CharSequence rawPassword, String encodedPassword) {
    Long shared = sharedDeadline.get();
    long deadline = shared != null ? shared : System.nanoTime() + waitNanos;
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
          return encoder.matches(rawPassword, encodedPassword);
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
