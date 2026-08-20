package com.researchspace.core.util.throttling;

/**
 * Exception thrown when throttling limit has been exceeded.
 */
public class TooManyRequestsException extends ThrottlingException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3649123646426430959L;

	public TooManyRequestsException(String message) {
		super(message);
	}

}
