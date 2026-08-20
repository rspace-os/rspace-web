package com.researchspace.model;

/**
 * Various ways User got authenticated within RSpace
 */
public enum UserAuthenticationMethod {

	SHIRO_SESSION,

	API_KEY,

	API_OAUTH_TOKEN,

	UI_OAUTH_TOKEN;

}