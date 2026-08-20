package com.researchspace.core.util.throttling;

public enum ThrottleInterval {

	QUARTER_MIN(15),

	HOUR(3600),

	DAY(86400);

	private long seconds;

	private ThrottleInterval(long seconds) {
		this.seconds = seconds;
	}

	public long getSeconds() {
		return seconds;
	}

}
