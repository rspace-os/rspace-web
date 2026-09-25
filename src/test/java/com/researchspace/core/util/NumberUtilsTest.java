package com.researchspace.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NumberUtilsTest {

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testStringToInt() {
    assertEquals(23, NumberUtils.stringToInt("23", 1));
    assertEquals(1, NumberUtils.stringToInt("25.3", 1));
    assertEquals(1, NumberUtils.stringToInt("abcd", 1));
  }
}
