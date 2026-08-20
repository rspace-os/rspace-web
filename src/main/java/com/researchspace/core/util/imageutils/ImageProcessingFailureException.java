package com.researchspace.core.util.imageutils;

/**
 * Runtime exception for failure to process image.
 */
public class ImageProcessingFailureException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public ImageProcessingFailureException (String msg, Exception e) {
		super(msg, e);
	}
	
	public ImageProcessingFailureException (String msg) {
		super(msg);
	}

}
