package com.researchspace.auth.password;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Runs every login and reauthentication password check through one shared pool of permits, so
 * Argon2's per-verification heap allocation is bounded however many requests arrive (ADR 0011). A
 * username has at most one check in flight, so one account cannot hold more than one permit.
 */
public class BoundedPasswordVerifier {

  /**
   * Outcome of a verification.
   *
   * @param matches whether the password matched
   * @param upgradedHash the password re-encoded with the current default when it matched against an
   *     outdated encoding, otherwise null
   */
  public record Result(boolean matches, String upgradedHash) {}

  private final PasswordEncoder encoder;
  private final Semaphore permits;
  private final long waitNanos;
  private final ConcurrentHashMap<String, PrincipalLock> principalLocks = new ConcurrentHashMap<>();

  public BoundedPasswordVerifier(PasswordEncoder encoder, int permits, Duration wait) {
    this.encoder = encoder;
    this.permits = new Semaphore(permits, true);
    this.waitNanos = wait.toNanos();
  }

  /**
   * Checks a password, waiting for the username's turn and then for a free permit, together bounded
   * by the configured wait.
   *
   * @throws LoginVerificationBusyException if the wait elapses first
   */
  public Result verify(String username, CharSequence rawPassword, String encodedPassword) {
    long deadline = System.nanoTime() + waitNanos;
    PrincipalLock principalLock = acquireHolder(username);
    try {
      if (!principalLock.lock.tryLock(remaining(deadline), TimeUnit.NANOSECONDS)) {
        throw busy(username);
      }
      try {
        if (!permits.tryAcquire(remaining(deadline), TimeUnit.NANOSECONDS)) {
          throw busy(username);
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
      throw busy(username);
    } finally {
      releaseHolder(username);
    }
  }

  /** Encodes a new password with the current default encoding. */
  public String encode(CharSequence rawPassword) {
    return encoder.encode(rawPassword);
  }

  private Result check(CharSequence rawPassword, String encodedPassword) {
    if (!encoder.matches(rawPassword, encodedPassword)) {
      return new Result(false, null);
    }
    String upgraded = encoder.upgradeEncoding(encodedPassword) ? encoder.encode(rawPassword) : null;
    return new Result(true, upgraded);
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

  private static LoginVerificationBusyException busy(String username) {
    return new LoginVerificationBusyException(
        "Password verification for [" + username + "] timed out waiting for a free slot");
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
