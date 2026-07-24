package com.researchspace.core.util;

/** Workaround for LANG-1770 until Apache Commons Lang 3.21.0. */
public final class StringAbbreviationUtils {
  private static final String ABBREVIATION_MARKER = "...";

  private StringAbbreviationUtils() {}

  public static String abbreviate(String string, int maxWidth) {
    if (string == null || string.isEmpty()) {
      return string;
    }
    if (maxWidth < ABBREVIATION_MARKER.length() + 1) {
      throw new IllegalArgumentException("Minimum abbreviation width is 4");
    }
    if (string.length() <= maxWidth) {
      return string;
    }

    int abbreviationEnd = maxWidth - ABBREVIATION_MARKER.length();
    if (Character.isHighSurrogate(string.charAt(abbreviationEnd - 1))
        && Character.isLowSurrogate(string.charAt(abbreviationEnd))) {
      abbreviationEnd--;
    }
    return string.substring(0, abbreviationEnd) + ABBREVIATION_MARKER;
  }
}
