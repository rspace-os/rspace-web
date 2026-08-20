package com.researchspace.model.preference;

public enum PreferenceCategory {
	
	MESSAGING("Messaging and notifications"),
	
	MESSAGING_BROADCAST("Preferred delivery method"),
	
	EXTERNAL_DATA("External data"), 
	
	/**
	 * Category for personalization of UI
	 */
	UI("User interface"),
	
	INTEGRATIONS("Integrations configuration");
	
	
	private String displayString;

	public String getDisplayString() {
		return displayString;
	}

	PreferenceCategory(String displayString) {
		this.displayString = displayString;
	}

}
