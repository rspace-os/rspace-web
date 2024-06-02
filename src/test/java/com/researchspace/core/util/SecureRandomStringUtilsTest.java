package com.researchspace.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.lang.StringUtils;
import org.junit.Test;

public class SecureRandomStringUtilsTest {
  @Test(expected = IllegalArgumentException.class)
  public void getURLSafeSecureRandomStringThrowsIAEIfInvalidLength() {
    SecureStringUtils.getURLSafeSecureRandomString(0);
  }

  @Test
  public void getURLSafeSecureRandomString() {
    int EXPECTED_LENGTH = 1;
    String random = SecureStringUtils.getURLSafeSecureRandomString(EXPECTED_LENGTH);
    assertTrue(EXPECTED_LENGTH <= random.length());
  }

  @Test
  public void getAlphanumericRandom() {
    int length = 32;
    String randomAlphaNumeric = SecureStringUtils.getSecureRandomAlphanumeric(length);
    assertEquals(length, randomAlphaNumeric.length());
    assertTrue(StringUtils.isAlphanumeric(randomAlphaNumeric));
  }
}
