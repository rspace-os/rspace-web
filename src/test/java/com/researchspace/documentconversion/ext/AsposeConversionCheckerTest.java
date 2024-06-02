package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AsposeConversionCheckerTest {

  AsposeConversionChecker checker;

  @BeforeEach
  public void setUp() {
    checker = new AsposeConversionChecker();
  }

  @Test
  public void validConversion() {
    boolean result = checker.supportsConversion("ppt", "png");
    assertTrue(result);
  }

  @Test
  public void fromFileTypeNotSupported() {
    boolean result = checker.supportsConversion("xyz", "html");
    assertFalse(result);
  }

  @Test
  public void toFileTypeNotSupported() {
    boolean result = checker.supportsConversion("csv", "mp3");
    assertFalse(result);
  }

  @Test
  public void neitherFileTypeSupported() {
    boolean result = checker.supportsConversion("xyz", "abc");
    assertFalse(result);
  }
}
