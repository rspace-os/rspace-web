package com.researchspace.core.testutil;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;

public class CoreTestUtils {

  /** Returns a random alphabetic string of the requested length. */
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
