package com.researchspace.core.util;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Hash;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.lang.util.ByteSource;

/** Utility class for methods for generating secure random Strings */
public class SecureStringUtils {

  private static RandomNumberGenerator randNumGen = new SecureRandomNumberGenerator();

  /**
   * Generates a securely random string of specified length safe for use in URLs
   *
   * @param length
   * @return a secure random string of at least the specified length; it may be longer due to
   *     vagaries of byte->hex string conversion
   * @throws IllegalArgumentException if <code>length</code> < 1
   */
  public static String getURLSafeSecureRandomString(int length) {
    if (length < 1) {
      throw new IllegalArgumentException("Length must be >=1 but was " + length);
    }
    ByteSource randomNumber = randNumGen.nextBytes(length);

    return Base64.encodeBase64URLSafeString(randomNumber.getBytes());
  }

  private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  public static String getSecureRandomAlphanumeric(int length) {
    StringBuilder sb = new StringBuilder(length);
    ByteSource randomNumber = randNumGen.nextBytes(length);
    for (int b : randomNumber.getBytes()) {
      b = Math.abs(b % AB.length());
      sb.append(AB.charAt(b));
    }
    return sb.toString();
  }

  /**
   * Cleans a search term of wild cards
   *
   * @param term
   * @return
   */
  public static String removeWildCards(String term) {
    if (!StringUtils.isEmpty(term)) {
      term = term.replaceAll("%|\\*|\\?", "");
    }
    return term;
  }

  public static Hash getHashForSigning(String content) {
    return new Sha256Hash(content);
  }
}
