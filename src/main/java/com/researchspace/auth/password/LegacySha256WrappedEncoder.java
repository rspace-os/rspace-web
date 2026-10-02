package com.researchspace.auth.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Verifies pre-Argon2 login password hashes that were wrapped at rest by Liquibase. A legacy hash
 * is one round of SHA-256 over the salt bytes (when present) followed by the UTF-8 password, as
 * Shiro's {@code Sha256Hash} computed it. The wrapped value is {@code <base64 salt>$<argon2>}, with
 * an empty salt for unsalted rows, where the Argon2 input is the Base64 of the 32 SHA-256 bytes.
 * Hashing the decoded bytes rather than the stored hex makes the hex case irrelevant.
 */
public class LegacySha256WrappedEncoder implements PasswordEncoder {

  private static final char SEPARATOR = '$';

  private final PasswordEncoder argon2;

  public LegacySha256WrappedEncoder(PasswordEncoder argon2) {
    this.argon2 = argon2;
  }

  /**
   * Wraps a stored legacy hash.
   *
   * @param sha256Hex the stored SHA-256 hex digest, either case
   * @param base64Salt the stored Base64 salt, or null for unsalted rows
   * @return the wrapped value, without the encoder id prefix
   */
  public String wrap(String sha256Hex, String base64Salt) {
    byte[] digest = HexFormat.of().parseHex(sha256Hex);
    String salt = base64Salt == null ? "" : base64Salt;
    return salt + SEPARATOR + argon2.encode(argon2Input(digest));
  }

  /** New passwords are encoded as plain Argon2id, never in the legacy format. */
  @Override
  public String encode(CharSequence rawPassword) {
    throw new UnsupportedOperationException("Legacy password hashes are only verified");
  }

  @Override
  public boolean matches(CharSequence rawPassword, String encodedPassword) {
    int separator = encodedPassword.indexOf(SEPARATOR);
    if (separator < 0) {
      throw new IllegalArgumentException("Malformed legacy password hash");
    }
    String salt = encodedPassword.substring(0, separator);
    String wrapped = encodedPassword.substring(separator + 1);
    byte[] digest = sha256(salt.isEmpty() ? null : Base64.getDecoder().decode(salt), rawPassword);
    return argon2.matches(argon2Input(digest), wrapped);
  }

  @Override
  public boolean upgradeEncoding(String encodedPassword) {
    return true;
  }

  private static String argon2Input(byte[] sha256Digest) {
    return Base64.getEncoder().encodeToString(sha256Digest);
  }

  private static byte[] sha256(byte[] salt, CharSequence rawPassword) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      if (salt != null) {
        digest.update(salt);
      }
      return digest.digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
