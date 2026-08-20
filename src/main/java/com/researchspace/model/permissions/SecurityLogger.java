package com.researchspace.model.permissions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does nothing except define a logger that can be used to write to a security
 * event log. This logger is configured in log4j.xml
 */
public class SecurityLogger {

	public static Logger log = LoggerFactory.getLogger(SecurityLogger.class);

}
