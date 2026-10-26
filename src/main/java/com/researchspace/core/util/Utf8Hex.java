package com.researchspace.core.util;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Lowercase hex of a string's UTF-8 bytes. Comparing this form in SQL matches text exactly,
 * independent of database collation, so it must stay in step with {@code
 * RsqlSqlFragments.exactTextIdentity}.
 */
public final class Utf8Hex {

  private Utf8Hex() {}

  public static String encode(String value) {
    return HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));
  }
}
