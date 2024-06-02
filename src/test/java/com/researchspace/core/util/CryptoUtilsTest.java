package com.researchspace.core.util;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class CryptoUtilsTest {

  @Test
  public void randomKeyIsUnique() {
    for (int i = 20; i < 200; ++i) {
      byte[] key1 = CryptoUtils.generateRandomKey(i);
      byte[] key2 = CryptoUtils.generateRandomKey(i);
      assertTrue(key1.length * 8 >= i);
      assertTrue(key2.length * 8 >= i);
      assertFalse(Arrays.equals(key1, key2));
    }
  }

  @Test
  public void sha256Works() {
    assertEquals(
        "090b235e9eb8f197f2dd927937222c570396d971222d9009a9189e2b6cc0a2c1",
        CryptoUtils.hashWithSha256inHex("haha"));
    assertEquals(
        "5f1e48b4aa1da45b2498c8056b7f2c90bcabbcc7c233731f6d23ecfdef3ab741",
        CryptoUtils.hashWithSha256inHex(
            "f1zKVsaaRwudB6+0PnjXtU/JGpweYS5PfWYM50pbVXz2ANU37BNXYx+0k+CsEtLM"));
  }
}
