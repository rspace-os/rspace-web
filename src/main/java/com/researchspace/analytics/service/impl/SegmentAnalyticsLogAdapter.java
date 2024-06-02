package com.researchspace.analytics.service.impl;

import com.segment.analytics.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements segment Log interface and passes analytics library DEBUG and ERROR level
 * messages to RSpace loggers.
 */
public class SegmentAnalyticsLogAdapter implements Log {
  private static final Logger log = LoggerFactory.getLogger(SegmentAnalyticsLogAdapter.class);

  @Override
  public void print(Level level, String format, Object... args) {
    if (!Level.VERBOSE.equals(level)) {
      log.info(String.format(format, args));
    }
  }

  @Override
  public void print(Level level, Throwable error, String format, Object... args) {
    if (!Level.VERBOSE.equals(level)) {
      log.info(String.format(format, args), error);
    }
  }
}
