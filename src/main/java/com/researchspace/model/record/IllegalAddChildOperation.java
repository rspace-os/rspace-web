package com.researchspace.model.record;

/**
 * This exception is thrown if an addChild operation to a Folder is not
 * permitted.
 */
public class IllegalAddChildOperation extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public IllegalAddChildOperation(String message) {
		super(message);
	}

	public IllegalAddChildOperation(String message, Exception cause) {
		super(message, cause);
	}

}
