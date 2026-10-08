package com.researchspace.auth.password;

import java.util.concurrent.Semaphore;
import org.apache.commons.lang3.Validate;

/**
 * Shared pool of permits held while sign-up, Google sign-up, LDAP first-login auto-signup and the
 * password-reset replies (both the login and the verification password; the verification reply is
 * BCrypt until PR #1204 moves it to Argon2, and takes a permit now so the two forms stay identical)
 * hash and save a new password, so anonymous Argon2 allocation is bounded by the permit count (ADR
 * 0011). A caller that finds no free permit is refused at once; nothing waits.
 */
public class NewPasswordEncodeGate {

  private final Semaphore permits;

  public NewPasswordEncodeGate(int permits) {
    Validate.isTrue(permits >= 1, "New-password encoding needs at least 1 permit, got %d", permits);
    this.permits = new Semaphore(permits, true);
  }

  /** Takes a permit if one is free, without waiting. */
  public boolean tryAcquire() {
    return permits.tryAcquire();
  }

  /** Returns a permit taken by {@link #tryAcquire()}. */
  public void release() {
    permits.release();
  }
}
