package com.researchspace.model.oauth;

/**
 * Origin of oauth token
 */
public enum OAuthTokenType {

	/**
	 * Token to be used by RSpace UI, generated during shiro-authenticated user session */
	UI_TOKEN,

	/**
	 * Token to be used by external API clients, generated with /oauth endpoint
	 */
	API_GENERATED_TOKEN

}
