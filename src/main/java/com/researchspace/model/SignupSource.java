package com.researchspace.model;

/**
 * Origin of user's account.
 */
public enum SignupSource {
	/**
	 * USer either signed up or was created by sysadmin, or batchuploaded. I.e.
	 * user identity was created in RSpace
	 */
	MANUAL,

	/**
	 * Account created from Google profile
	 */
	GOOGLE,

	/**
	 * Account created from single signon
	 */
	SSO,
	
	/**
	 * Account managed by LDAP
	 */
	LDAP,
	
	/**
	 * User account created programmatically - e.g. admin account, test account
	 * etc
	 */
	INTERNAL,
	
	/**
	 * user created on SSO system as a backdoor non-SSO account (RSPAC-2189)
	 */
	SSO_BACKDOOR
}
