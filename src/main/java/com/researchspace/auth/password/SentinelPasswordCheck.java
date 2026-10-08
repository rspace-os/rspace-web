package com.researchspace.auth.password;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Runs a login attempt for a username with no password to check through the same Argon2 check as a
 * real one, so the response time does not reveal whether the username exists. All such attempts
 * share one per-username lock in {@link BoundedPasswordVerifier} and so hold at most one permit: a
 * flood of made-up names cannot take the pool.
 */
public class SentinelPasswordCheck {

  /** Not a valid username, so the lock it takes is never a real user's. */
  static final String SENTINEL_USERNAME = "<unknown user>";

  private final BoundedPasswordVerifier verifier;
  private final String sentinelEncoded;

  public SentinelPasswordCheck(RSpacePasswordEncoder encoder, BoundedPasswordVerifier verifier) {
    this.verifier = verifier;
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    this.sentinelEncoded = encoder.encode(Base64.getEncoder().encodeToString(random));
  }

  /** Checks the supplied password against the sentinel hash and discards the answer. */
  public void pad(CharSequence suppliedPassword) {
    try {
      verifier.verify(SENTINEL_USERNAME, suppliedPassword, sentinelEncoded);
    } catch (LoginVerificationBusyException e) {
      // a busy pad is still a refusal
    }
  }
}
