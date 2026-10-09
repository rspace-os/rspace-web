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
 * permit, and at most one more caller may wait for that username, so a burst of requests for one
 * name cannot hold the admissions other accounts need. At most {@code permits + maxQueued} callers
 * are inside at once; further callers are refused as busy without waiting. New passwords are not
 * encoded here; anonymous encodes are bounded by {@link NewPasswordEncodeGate}.
 */
public class BoundedPasswordVerifier {

  /** One check in flight per username plus this many waiting for it; more are refused at once. */
  static final int MAX_WAITING_PER_PRINCIPAL = 1;

  private final PasswordEncoder encoder;
  private final Semaphore permits;
  private final Semaphore admissions;
  private final int maxPermits;
  private final long waitNanos;
  private final ConcurrentHashMap<String, PrincipalLock> principalLocks;

  /** The username lock this thread holds through {@link #runExclusive}, if any. */
  private final ThreadLocal<Held> held = new ThreadLocal<>();

  /**
   * @param permits checks that may run at once
   * @param maxQueued callers that may wait for a permit or an account's lock; more are refused
   * @param wait how long a caller waits before it is refused as busy
   */
  public BoundedPasswordVerifier(
      PasswordEncoder encoder, int permits, int maxQueued, Duration wait) {
    this(encoder, permits, maxQueued, wait, new ConcurrentHashMap<>());
  }

  BoundedPasswordVerifier(
      PasswordEncoder encoder,
      int permits,
      int maxQueued,
      Duration wait,
      ConcurrentHashMap<String, PrincipalLock> principalLocks) {
    Validate.notNull(encoder);
    Validate.isTrue(permits >= 1, "Password verification needs at least 1 permit, got %d", permits);
    Validate.isTrue(maxQueued >= 0, "Password verification queue must not be negative");
    Validate.isTrue(!wait.isNegative(), "Password verification wait must not be negative");
    this.encoder = encoder;
    this.maxPermits = permits;
    this.permits = new Semaphore(permits, true);
    this.admissions = new Semaphore(permits + maxQueued);
    this.waitNanos = wait.toNanos();
    this.principalLocks = principalLocks;
  }

  /**
   * Runs an action while holding the username's lock, the same lock {@link #verify} takes, so a
   * whole login attempt for one account runs one at a time. Waits up to the configured wait for the
   * lock, and a {@link #verify} on this thread inside the action shares that one wait. A nested
   * {@link #verify} for the same username re-enters the held lock and counts as neither a waiter
   * nor a new admission.
   *
   * @return the action's result
   * @throws LoginVerificationBusyException if the wait elapses first
   */
  public <T> T runExclusive(String username, Callable<T> action) throws Exception {
    PrincipalLock principalLock = acquireHolder(username);
    try {
      boolean admitted = admitOutermost(username);
      try {
        return lockAndRun(username, principalLock, action);
      } finally {
        if (admitted) {
          admissions.release();
        }
      }
    } finally {
      releaseHolder(username);
    }
  }

  private <T> T lockAndRun(String username, PrincipalLock principalLock, Callable<T> action)
      throws Exception {
    long deadline = System.nanoTime() + waitNanos;
    try {
      if (!principalLock.lock.tryLock(remaining(deadline), TimeUnit.NANOSECONDS)) {
        throw busy(username, "another login for this username is still running");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw busy(username, "interrupted while waiting");
    }
    Held outer = held.get();
    held.set(new Held(username, principalLock, deadline));
    try {
      return action.call();
    } finally {
      if (outer == null) {
        held.remove();
      } else {
        held.set(outer);
      }
      principalLock.lock.unlock();
    }
  }

  /**
   * Checks a password, waiting for the username's turn and then for a free permit, together bounded
   * by the configured wait.
   *
   * @return whether the password matched
   * @throws LoginVerificationBusyException if the wait elapses first, or too many callers are
   *     already waiting
   * @throws IllegalArgumentException if the stored value has no recognised encoding
   */
  public boolean verify(String username, CharSequence rawPassword, String encodedPassword) {
    Held outer = held.get();
    if (outer != null && outer.username.equals(username)) {
      return lockAndVerify(username, outer.lock, outer.deadline, rawPassword, encodedPassword);
    }
    long deadline = outer != null ? outer.deadline : System.nanoTime() + waitNanos;
    PrincipalLock principalLock = acquireHolder(username);
    try {
      boolean admitted = admitOutermost(username);
      try {
        return lockAndVerify(username, principalLock, deadline, rawPassword, encodedPassword);
      } finally {
        if (admitted) {
          admissions.release();
        }
      }
    } finally {
      releaseHolder(username);
    }
  }

  private boolean lockAndVerify(
      String username,
      PrincipalLock principalLock,
      long deadline,
      CharSequence rawPassword,
      String encodedPassword) {
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
    }
  }

  /** A call nested inside {@link #runExclusive} on this thread is already admitted. */
  private boolean admitOutermost(String username) {
    if (held.get() != null) {
      return false;
    }
    if (!admissions.tryAcquire()) {
      throw busy(username, "too many password checks are already waiting");
    }
    return true;
  }

  /**
   * Registers this caller on the username's entry, before any admission or wait. A caller beyond
   * the username's one running and one waiting check is refused in the same per-key step, so it
   * never enters the count.
   */
  private PrincipalLock acquireHolder(String username) {
    return principalLocks.compute(
        username,
        (k, existing) -> {
          if (existing != null && existing.holders > MAX_WAITING_PER_PRINCIPAL) {
            throw busy(username, "another check for this username is already waiting");
          }
          PrincipalLock l = existing == null ? new PrincipalLock() : existing;
          l.holders++;
          return l;
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

  private static final class Held {
    private final String username;
    private final PrincipalLock lock;
    private final long deadline;

    private Held(String username, PrincipalLock lock, long deadline) {
      this.username = username;
      this.lock = lock;
      this.deadline = deadline;
    }
  }

  /** Mutated only inside {@link ConcurrentHashMap#compute}, which serialises per key. */
  static final class PrincipalLock {
    private final ReentrantLock lock = new ReentrantLock();
    private int holders;
  }

  int availableAdmissions() {
    return admissions.availablePermits();
  }

  int availablePermits() {
    return permits.availablePermits();
  }

  int trackedPrincipals() {
    return principalLocks.size();
  }
}
