package com.researchspace.auth.password;

import com.researchspace.model.User;
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
  private final ConcurrentHashMap<String, PrincipalLock> principalLocks = new ConcurrentHashMap<>();
  private final ThreadLocal<Long> sharedDeadline = new ThreadLocal<>();

  /**
   * @param permits checks that may run at once
   * @param maxQueued callers that may wait for a permit or an account's lock; more are refused
   * @param wait how long a caller waits before it is refused as busy
   */
  public BoundedPasswordVerifier(
      PasswordEncoder encoder, int permits, int maxQueued, Duration wait) {
    Validate.notNull(encoder);
    Validate.isTrue(permits >= 1, "Password verification needs at least 1 permit, got %d", permits);
    Validate.isTrue(maxQueued >= 0, "Password verification queue must not be negative");
    Validate.isTrue(!wait.isNegative(), "Password verification wait must not be negative");
    this.encoder = encoder;
    this.maxPermits = permits;
    this.permits = new Semaphore(permits, true);
    this.admissions = new Semaphore(permits + maxQueued);
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
  }

  /**
   * Checks a password, waiting for the username's turn and then for a free permit, together bounded
   * by the configured wait. A null password, or one longer than {@link User#MAX_PWD_LENGTH} (which
   * no stored hash can have been made from), is refused at once without a lock, permit or hash.
   *
   * @return whether the password matched
   * @throws LoginVerificationBusyException if the wait elapses first, or too many callers are
   *     already waiting
   * @throws IllegalArgumentException if the stored value has no recognised encoding
   */
  public boolean verify(String username, CharSequence rawPassword, String encodedPassword) {
    if (rawPassword == null || rawPassword.length() > User.MAX_PWD_LENGTH) {
      return false;
    }
    PrincipalLock principalLock = acquireHolder(username);
    try {
      boolean admitted = admitOutermost(username);
      try {
        return lockAndVerify(username, principalLock, rawPassword, encodedPassword);
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
      CharSequence rawPassword,
      String encodedPassword) {
    Long shared = sharedDeadline.get();
    long deadline = shared != null ? shared : System.nanoTime() + waitNanos;
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
    if (sharedDeadline.get() != null) {
      return false;
    }
    if (!admissions.tryAcquire()) {
      throw busy(username, "too many password checks are already waiting");
    }
    return true;
  }

  /**
   * Registers this caller on the username's entry, before any admission or wait, and refuses it at
   * once if the username already has its one check running and one waiting.
   */
  private PrincipalLock acquireHolder(String username) {
    PrincipalLock lock =
        principalLocks.compute(
            username,
            (k, existing) -> {
              PrincipalLock l = existing == null ? new PrincipalLock() : existing;
              l.holders++;
              return l;
            });
    if (lock.holders > 1 + MAX_WAITING_PER_PRINCIPAL) {
      releaseHolder(username);
      throw busy(username, "another check for this username is already waiting");
    }
    return lock;
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
