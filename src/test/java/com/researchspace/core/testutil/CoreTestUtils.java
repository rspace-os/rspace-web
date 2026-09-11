package com.researchspace.core.testutil;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;

public class CoreTestUtils {

  /**
   * Asserts that a particular exception is thrown.
   *
   * <p>Use junit5 assertThrows for new tests.
   *
   * @param invokable
   * @param clazz
   * @throws Exception
   */
  public static void assertExceptionThrown(Invokable invokable, Class<? extends Throwable> clazz)
      throws Exception {
    assertExceptionThrown(invokable, clazz, Matchers.anything());
  }

  /**
   * Asserts that an exception of class clazz is thrown. If it is, then additionally asserts the
   * supplied matcher against the exception.getMessage()
   *
   * @param invokable
   * @param clazz
   * @param matcher
   * @throws Exception
   */
  public static void assertExceptionThrown(
      Invokable invokable, Class<? extends Throwable> clazz, Matcher<?> matcher) throws Exception {
    try {
      invokable.invoke();
      fail("Exception " + clazz.getName() + " was not thrown");
    } catch (Exception e) {
      if (clazz.isAssignableFrom(e.getClass())) {
        assertTrue(
            matcher.matches(e.getMessage()),
            "Exception message '" + e.getMessage() + "' didn't match");
      } else {
        throw e;
      }
    }
  }

  /**
   * Helper for easier calling of input validation tests
   *
   * @param executable
   */
  public static void assertIllegalArgumentException(Executable executable) {
    Assertions.assertThrows(IllegalArgumentException.class, executable);
  }

  /**
   * Helper for easier calling of input validation tests
   *
   * @param executable
   */
  public static void assertIllegalStateExceptionThrown(Executable executable) {
    Assertions.assertThrows(IllegalStateException.class, executable);
  }

  public static String getRandomName(int length) {
    return RandomStringUtils.randomAlphabetic(length);
  }

  public static StringAppenderForTestLogging configureStringLogger(
      org.apache.logging.log4j.Logger log) {
    LoggerContext ctx = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
    Configuration configuration = ctx.getConfiguration();
    StringAppenderForTestLogging stringAppender =
        new StringAppenderForTestLogging("stringAppender", null, null, false, null);
    for (Appender appender : configuration.getAppenders().values()) {
      configuration.getLoggerConfig(log.getName()).removeAppender(appender.getName());
      configuration.getRootLogger().removeAppender(appender.getName());
    }
    stringAppender.start();
    configuration.addLoggerAppender((Logger) log, stringAppender);
    configuration.getLoggerConfig(log.getName()).setLevel(Level.INFO);
    ctx.updateLoggers();
    return stringAppender;
  }
}
