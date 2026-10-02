package com.researchspace.auth.password;

import java.util.Map;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Encoder for login and verification passwords. Stored values carry an {@code {id}} prefix; new
 * passwords are encoded with Argon2id under {@link #ARGON2_ID}. Only the ids registered here are
 * accepted, so an unknown or missing prefix fails with {@link IllegalArgumentException} rather than
 * matching. {@link #BCRYPT_ID} is registered for verification passwords set before RSDEV-894 and
 * never encodes; {@link com.researchspace.auth.UsernamePasswordCredentialsMatcher} refuses it for
 * login passwords. See ADR 0011.
 */
public class RSpacePasswordEncoder implements PasswordEncoder {

  public static final String ARGON2_ID = "argon2@rspace_v1";
  public static final String LEGACY_SHA256_ID = "argon2-legacy-sha256@rspace_v1";
  public static final String BCRYPT_ID = "bcrypt";

  private static final int SALT_LENGTH = 16;
  private static final int HASH_LENGTH = 32;
  private static final int PARALLELISM = 1;
  private static final int MEMORY_KIB = 19456;
  private static final int ITERATIONS = 2;

  private final LegacySha256WrappedEncoder legacy;
  private final DelegatingPasswordEncoder delegate;

  public RSpacePasswordEncoder() {
    PasswordEncoder argon2 =
        new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);
    legacy = new LegacySha256WrappedEncoder(argon2);
    delegate =
        new DelegatingPasswordEncoder(
            ARGON2_ID,
            Map.of(
                ARGON2_ID,
                argon2,
                LEGACY_SHA256_ID,
                legacy,
                BCRYPT_ID,
                new BCryptPasswordEncoder()));
  }

  /**
   * Wraps a pre-Argon2 SHA-256 hash for storage, for the at-rest migration.
   *
   * @param sha256Hex the stored SHA-256 hex digest, either case
   * @param base64Salt the stored Base64 salt, or null for unsalted rows
   * @return the prefixed value to store in place of the hash; the salt is inside it, so the caller
   *     clears the salt column
   */
  public String wrapLegacySha256(String sha256Hex, String base64Salt) {
    return "{" + LEGACY_SHA256_ID + "}" + legacy.wrap(sha256Hex, base64Salt);
  }

  @Override
  public String encode(CharSequence rawPassword) {
    return delegate.encode(rawPassword);
  }

  /**
   * @throws IllegalArgumentException if the stored value has no registered id prefix
   */
  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    if (rawPassword == null || encodedPassword == null) {
      return false;
    }
    return delegate.matches(rawPassword, encodedPassword);
  }

  @Override
  public boolean upgradeEncoding(String encodedPassword) {
    return delegate.upgradeEncoding(encodedPassword);
  }
}
