package com.researchspace.auth.password;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Throwaway check that Argon2 runs on the resolved BouncyCastle; removed in RSDEV-894 phase 1. */
class Argon2SmokeTest {

  @Test
  void encodesAndMatches() {
    System.out.println(
        "Argon2BytesGenerator from "
            + Argon2BytesGenerator.class.getProtectionDomain().getCodeSource().getLocation());
    Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    String encoded = encoder.encode("password1");
    assertTrue(encoded.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
    assertTrue(encoder.matches("password1", encoded));
    assertFalse(encoder.matches("password2", encoded));
  }
}
