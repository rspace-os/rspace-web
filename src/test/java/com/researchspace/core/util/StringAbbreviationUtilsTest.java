package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class StringAbbreviationUtilsTest {

  @Test
  public void doesNotSplitSurrogatePairAtAbbreviationBoundary() {
    assertEquals("abc...", StringAbbreviationUtils.abbreviate("abc\uD83D\uDE00def", 7));
  }

  @Test
  public void returnsEmptyStringBeforeValidatingWidth() {
    assertEquals("", StringAbbreviationUtils.abbreviate("", 0));
  }

  @Test
  public void retainsSupplementaryCodePointWhenItFitsWithinWidth() {
    assertEquals(
        "abc\uD83D\uDE00...", StringAbbreviationUtils.abbreviate("abc\uD83D\uDE00defg", 8));
  }
}
