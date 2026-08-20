package com.researchspace.core.util;

import org.joda.time.DateTime;

/**
 * Default implementation uses the real time
 */
public class DefaultTimeSource implements TimeSource {

	@Override
	public DateTime now() {
		return new DateTime();
	}

}
