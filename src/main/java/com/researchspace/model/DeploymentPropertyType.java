package com.researchspace.model;


/**
 * Identifier for a property type that is used to determine if methods annotated with DeploymentProperty
 * annotation can be accessed or not.
 */
public enum DeploymentPropertyType {

	/**
	 * property saying that external network file store is configured and
	 * available through the gallery
	 */
	NET_FILE_STORES_ENABLED,

	/**
	 * User signup should be enabled
	 */
	USER_SIGNUP_ENABLED,
	
	/**
	 * User's profile email is editable
	 */
	PROFILE_EMAIL_EDITABLE,
	
	/**
	 * non-public/final API methods
	 */
	API_BETA_ENABLED

}
