package com.researchspace.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

public class SecureRandomStringUtilsTest {
  @Test
  public void getURLSafeSecureRandomStringThrowsIAEIfInvalidLength() {
    assertThrows(
        IllegalArgumentException.class, () -> SecureStringUtils.getURLSafeSecureRandomString(0));
  }

  @Test
  public void getURLSafeSecureRandomString() {
    int EXPECTED_LENGTH = 1;
    String random = SecureStringUtils.getURLSafeSecureRandomString(EXPECTED_LENGTH);
    assertThat(random).hasSizeGreaterThanOrEqualTo(EXPECTED_LENGTH);
  }

  @Test
  public void getAlphanumericRandom() {
    int length = 32;
    String randomAlphaNumeric = SecureStringUtils.getSecureRandomAlphanumeric(length);
    assertThat(randomAlphaNumeric).hasSize(length);
    assertTrue(StringUtils.isAlphanumeric(randomAlphaNumeric));
  }
}
