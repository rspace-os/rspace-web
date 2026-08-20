package com.researchspace.core.util.throttling;

/**
 * Base class for any exceptions generated from throttling limits being reached.
 */
public class ThrottlingException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2131894624554243818L;
	

	public ThrottlingException(String message) {
		super(message);
	}

}
