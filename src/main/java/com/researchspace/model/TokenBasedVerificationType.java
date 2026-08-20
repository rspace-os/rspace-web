package com.researchspace.model;

/**
 * Distinguishes different usages of TokenBasedVerification.
 */
public enum TokenBasedVerificationType {

	/**
	 * A token-based approach to verify a password change request default
	 * timeout = 1 hour
	 */
	PASSWORD_CHANGE(1000 * 60 * 60),

	/**
	 * A token based signup validation, default timeout = 3 weeks
	 */
	VERIFIED_SIGNUP(1000 * 60 * 60 * 24 * 21),

	/**
	 * A token based validation of new email address. After following the link
	 * users needs to log in, so there is little risk. Default timeout same as
	 * VERIFIED_SIGNUP (3 weeks)
	 */
	EMAIL_CHANGE(1000 * 60 * 60 * 24 * 21);

	public long getTimeout() {
		return timeout;
	}

	private TokenBasedVerificationType(long timeout) {
		this.timeout = timeout;
	}

	private long timeout;

}
