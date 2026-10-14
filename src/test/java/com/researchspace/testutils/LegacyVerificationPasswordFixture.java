package com.researchspace.testutils;

public final class LegacyVerificationPasswordFixture {

  public static final String PLAIN = "verify1234";

  // BCrypt.hashpw(PLAIN, BCrypt.gensalt()), as stored before RSDEV-894
  public static final String BCRYPT_HASH =
      "$2a$10$fqWevKAPMKNsortKy6gS9eZbYfMnuItTnN4KUf2cy0w0dMTcIjzA6";

  private LegacyVerificationPasswordFixture() {}
}
