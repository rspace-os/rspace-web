package com.researchspace.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does nothing except define a logger that can be used to write to a failed email logger event log.
 * This logger is configured in log4j2.xml
 */
public class FailedEmailLogger {

  public static Logger errorLog = LoggerFactory.getLogger(FailedEmailLogger.class);
}
