package com.researchspace.api.v1.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does nothing except define a logger that can be used to write to an API requests event log. This
 * logger is configured in log4j2.xml
 */
public class ApiRequestLogger {

  public static Logger apiRequestLog = LoggerFactory.getLogger(ApiRequestLogger.class);
}
