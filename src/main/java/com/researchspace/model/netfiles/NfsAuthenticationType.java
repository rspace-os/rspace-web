package com.researchspace.model.netfiles;

/**
 * Enum representing authentication methods supported by external file systems.
 */
public enum NfsAuthenticationType {

	/**
	 * username/password type of authentication
	 */
	PASSWORD,
	
	/**
	 * public/private key authentication
	 */
	PUBKEY,

	/**
	 * if user doesn't need to explicitly authenticate
	 */
	NONE
	
}
