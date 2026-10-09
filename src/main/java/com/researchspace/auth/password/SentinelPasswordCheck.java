package com.researchspace.auth.password;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Runs a login attempt for a username with no password to check through the same Argon2 check as a
 * real one, so the response time does not reveal whether the username exists. Each submitted name
 * queues on its own lock and takes one permit in {@link BoundedPasswordVerifier}, exactly as a real
 * username does, so admission timing does not depend on whether the name exists. A burst of made-up
 * names can therefore occupy permits, as a burst of real names already can; the pool size is the
 * bound either way.
 */
public class SentinelPasswordCheck {

  private final BoundedPasswordVerifier verifier;
  private final String sentinelEncoded;

  public SentinelPasswordCheck(RSpacePasswordEncoder encoder, BoundedPasswordVerifier verifier) {
    this.verifier = verifier;
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    this.sentinelEncoded = encoder.encode(Base64.getEncoder().encodeToString(random));
  }

  /** NUL is not a valid username character, so the key never collides with a real user's lock. */
  static String sentinelKey(String username) {
    return "\0sentinel:" + username;
  }

  /** Checks the supplied password against the sentinel hash and discards the answer. */
  public void pad(String username, CharSequence suppliedPassword) {
    try {
      verifier.verify(sentinelKey(username), suppliedPassword, sentinelEncoded);
    } catch (LoginVerificationBusyException e) {
      // a busy pad is still a refusal
    }
  }
}
