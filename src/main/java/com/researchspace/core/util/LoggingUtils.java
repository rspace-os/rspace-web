package com.researchspace.core.util;

import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingUtils {
  /**
   * Generates an identifier for a log event
   *
   * @return
   */
  public static String generateLogId() {
    return RandomStringUtils.randomAlphabetic(10);
  }

  private static Logger log = LoggerFactory.getLogger(LoggingUtils.class);

  /**
   * General utility to log an exception at ERROR if normal logging code is hard to use (e.g. in a
   * jsp page).
   *
   * @param exception
   * @return an ID of the logged exception (the id is included in the log message)
   */
  public static String logException(Throwable exception) {
    String id = generateLogId();
    log.error("Exception id [{}] thrown: {}", id, exception.getMessage());
    return id;
  }
}
