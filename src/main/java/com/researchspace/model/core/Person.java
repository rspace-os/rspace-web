package com.researchspace.model.core;

/**
 * Top-level interface to represent a person or account in RSpace.
 */
public interface Person {
	
	/**
	 * Core regex for allowed username characters
	 */
	String ALLOWED_USERNAME_CHARS = "A-Za-z0-9@#\\.\\-";
	
	/**
	 * Core regex for allowed username without length restriction
	 */
	String ALLOWED_USERNAME_CHARS_RELAXED_SUBREGEX = "[" + ALLOWED_USERNAME_CHARS + "]{1,}";
	
	
	String getEmail();
	
	String getFullName();
	
	String getUniqueName();
	
	Object getId();

}
