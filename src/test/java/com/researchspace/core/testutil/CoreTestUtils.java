package com.researchspace.core.testutil;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

public class CoreTestUtils {

  private static final String STRING_APPENDER = "stringAppender";

  public static String getRandomName(int length) {
    return RandomStringUtils.randomAlphabetic(length);
  }

  public static StringAppenderForTestLogging configureStringLogger(
      org.apache.logging.log4j.Logger log) {
    LoggerContext ctx = (org.apache.logging.log4j.core.LoggerContext) LogManager.getContext(false);
    Configuration configuration = ctx.getConfiguration();
    StringAppenderForTestLogging stringAppender =
        new StringAppenderForTestLogging(STRING_APPENDER, null, null, false, null);
    stringAppender.start();
    // replace only an earlier string appender: the configuration lives for the whole test JVM
    configuration.getLoggerConfig(log.getName()).removeAppender(STRING_APPENDER);
    configuration.addLoggerAppender((Logger) log, stringAppender);
    configuration.getLoggerConfig(log.getName()).setLevel(Level.INFO);
    ctx.updateLoggers();
    return stringAppender;
  }
}
