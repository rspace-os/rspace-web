package com.researchspace.core.util;

import org.joda.time.DateTime;

/**
 * Abstraction of a time provider to allow easier testing.
 */
public interface TimeSource {

	/**
	 * Gets current time instant in milliseconds
	 * 
	 * @return a {@link DateTime} for current instant.
	 */
	DateTime now();

}
