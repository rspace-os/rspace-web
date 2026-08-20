package com.researchspace.model.preference;

/**
 * Possible values for BOX_VERSIONED_LINKS preference
 */
public enum BoxLinkType {

	/** user wants standard 'live' links pointing to latest version */
	LIVE,
	
	/** link will point to document version that was in use at the time of link creation */
	VERSIONED,

	/** user wants to be asked individually for every link */
	ASK
	
}
