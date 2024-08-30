package com.researchspace.core.util;

import static javax.xml.bind.DatatypeConverter.printBase64Binary;

import com.google.common.io.BaseEncoding;
import org.apache.shiro.crypto.RandomNumberGenerator;
import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.apache.shiro.util.ByteSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class CryptoUtils {
  private static RandomNumberGenerator randNumGen = new SecureRandomNumberGenerator();
  private static BCryptPasswordEncoder bCryptEncoder = new BCryptPasswordEncoder();
  private static final int KEY_LENGTH = 192;

  /**
   * Hash data with SHA-256 and return the hash in hex
   *
   * @param plainText data to hash, equivalent to running `sha256sum` with -t meaning text mode
   * @return 64 character long hash
   */
  public static String hashWithSha256inHex(String plainText) {
    return new Sha256Hash(plainText).toHex();
  }

  /** Salt the data, hash it with SHA-256 and return the hash in hex */
  public static String hashWithSha256inHex(String plainText, ByteSource salt) {
    return new Sha256Hash(plainText, salt).toHex();
  }

  /**
   * @param numberOfBits ideally divisible by 8
   * @return array of random bytes containing AT LEAST numberOfBits bits
   */
  public static byte[] generateRandomKey(int numberOfBits) {
    return randNumGen.nextBytes((numberOfBits + 7) / 8).getBytes();
  }

  /**
   * Converts binary data to base64 and removes trailing = signs which serve as padding in base64 to
   * make the string length divisible by 4.
   *
   * @param data binary data to be encoded
   * @return the data encoded as base64 with minimal amount of characters
   */
  public static String toCompactBase64(byte[] data) {
    String base64 = printBase64Binary(data);
    return base64.split("=")[0];
  }

  /**
   * Converts binary data to base32 and removes trailing = signs which serve as padding in base32 *
   * to make the string length divisible by 4.
   *
   * @param data binary data to be encoded
   * @return the data encoded as base32 with minimal amount of characters
   */
  public static String toCompactBase32(byte[] data) {
    String base32 = BaseEncoding.base32().encode(data);
    return base32.split("=")[0];
  }

  /**
   * Generates base64 representation of a cryptographically secure <code>KEY_LENGTH</code> bit
   * string.
   */
  private static String base64ofSecureRandomKey() {
    return toCompactBase64(generateRandomKey(KEY_LENGTH));
  }

  public static String generateUnhashedToken() {
    return base64ofSecureRandomKey();
  }

  public static String generateUnhashedClientSecret() {
    return base64ofSecureRandomKey();
  }

  public static String generateHashedClientSecret() {
    return hashClientSecret(generateUnhashedClientSecret());
  }

  public static String hashClientSecret(String unhashedClientSecret) {
    return hashWithSha256inHex(unhashedClientSecret);
  }

  public static String hashToken(String unhashedToken) {
    return hashWithSha256inHex(unhashedToken);
  }

  public static String encodeBCrypt(String clearSecret) {
    return bCryptEncoder.encode(clearSecret);
  }

  public static boolean matchBCrypt(String clearSecret, String encodedSecret) {
    return bCryptEncoder.matches(clearSecret, encodedSecret);
  }

  public static String generateClientId() {
    // base32 because base64 can have forward slashes, clientId is used as a path variable
    return toCompactBase32(generateRandomKey(KEY_LENGTH));
  }
}
