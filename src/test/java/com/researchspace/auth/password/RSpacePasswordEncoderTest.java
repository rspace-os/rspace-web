package com.researchspace.auth.password;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RSpacePasswordEncoderTest {

  // Generated with Shiro's Sha256Hash before its removal, as UserManagerImpl stored them.
  private static final String SALT = "AxQlNkdYaXqLnK2+z+DxAg==";
  private static final String SALTED_HEX =
      "500ae17802c8636f5983eab68f86e151e3f0cb054f3250be363c46f2335844c8"; // legacyPass1
  private static final String SALTED_NON_ASCII_HEX =
      "27495fa3f06b759e046a69b420893647edf34e542966b5f8fbce3565735b91e6"; // p£ssword
  // Unsalted, as in initial-seed-run.sql: sysWisc23!
  private static final String UNSALTED_HEX =
      "caa6eec0faa20efba3b7af44af7107b05334759954ef3581030cc8e6199a33bf";

  // BCrypt.hashpw("verify1234", BCrypt.gensalt()), as stored before RSDEV-894
  private static final String LEGACY_BCRYPT =
      "$2a$10$fqWevKAPMKNsortKy6gS9eZbYfMnuItTnN4KUf2cy0w0dMTcIjzA6";

  private final RSpacePasswordEncoder encoder = new RSpacePasswordEncoder();

  @Test
  void argon2RoundTrip() {
    String encoded = encoder.encode("password1");
    assertTrue(encoded.startsWith("{argon2@rspace_v1}$argon2id$v=19$m=19456,t=2,p=1$"));
    assertTrue(encoder.matches("password1", encoded));
    assertFalse(encoder.matches("password2", encoded));
  }

  @Test
  void legacySaltedHashMatches() {
    String wrapped = encoder.wrapLegacySha256(SALTED_HEX, SALT);
    assertTrue(wrapped.startsWith("{argon2-legacy-sha256@rspace_v1}" + SALT + "$$argon2id$"));
    assertTrue(encoder.matches("legacyPass1", wrapped));
    assertFalse(encoder.matches("legacyPass1x", wrapped));
  }

  @Test
  void legacyUnsaltedHashMatchesInEitherHexCase() {
    String lower = encoder.wrapLegacySha256(UNSALTED_HEX, null);
    String upper = encoder.wrapLegacySha256(UNSALTED_HEX.toUpperCase(), null);
    assertTrue(encoder.matches("sysWisc23!", lower));
    assertTrue(encoder.matches("sysWisc23!", upper));
    assertFalse(encoder.matches("sysWisc23", upper));
  }

  @Test
  void nonAsciiPasswordMatchesThroughBothFormats() {
    assertTrue(encoder.matches("p£ssword", encoder.encode("p£ssword")));
    assertTrue(encoder.matches("p£ssword", encoder.wrapLegacySha256(SALTED_NON_ASCII_HEX, SALT)));
  }

  @Test
  void unknownOrMissingPrefixFailsClosed() {
    String argon2 = encoder.encode("password1").substring("{argon2@rspace_v1}".length());
    assertThrows(IllegalArgumentException.class, () -> encoder.matches("password1", argon2));
    assertThrows(IllegalArgumentException.class, () -> encoder.matches("sysWisc23!", UNSALTED_HEX));
    assertThrows(IllegalArgumentException.class, () -> encoder.matches("x", "{noop}x"));
    assertThrows(IllegalArgumentException.class, () -> encoder.matches("x", "{sha256}x"));
    // a bare BCrypt value, as stored before the prefix migration
    assertThrows(
        IllegalArgumentException.class, () -> encoder.matches("verify1234", LEGACY_BCRYPT));
  }

  @Test
  void prefixedLegacyBcryptVerificationPasswordMatchesButIsNotUsedForNewHashes() {
    String prefixed = "{bcrypt}" + LEGACY_BCRYPT;
    assertTrue(encoder.matches("verify1234", prefixed));
    assertFalse(encoder.matches("verify12345", prefixed));
    assertFalse(encoder.encode("verify1234").startsWith("{bcrypt}"));
  }

  @Test
  void legacyHashWithoutSeparatorFailsClosed() {
    assertThrows(
        IllegalArgumentException.class,
        () -> encoder.matches("x", "{" + RSpacePasswordEncoder.LEGACY_SHA256_ID + "}nosep"));
  }

  @Test
  void nullsNeverMatch() {
    assertFalse(encoder.matches(null, null));
    assertFalse(encoder.matches("x", null));
  }
}
