package com.researchspace.testutils;

import org.junit.jupiter.api.Assumptions;

public class TestRunnerController {

  /** Will ignore tests if this is called from a {@code @BeforeAll} method. */
  public static void ignoreIfFastRun() {
    if (getFastProperty() != null) {
      Assumptions.assumeFalse(true);
    }
  }

  private static String getFastProperty() {
    return System.getProperty("fast");
  }

  /**
   * Boolean test for whether test is a fast run or not
   *
   * @return
   */
  public static boolean isFastRun() {
    return getFastProperty() != null;
  }

  /**
   * Boolean test for whether java is 11
   *
   * @return
   */
  public static boolean isJDK11() {
    return getJavaVersion().startsWith("11");
  }

  private static String getJavaVersion() {
    return System.getProperty("java.version");
  }

  /**
   * Boolean test for whether java is 8
   *
   * @return
   */
  public static boolean isJDK8() {
    return getJavaVersion().startsWith("8") || getJavaVersion().startsWith("1.8");
  }
}
