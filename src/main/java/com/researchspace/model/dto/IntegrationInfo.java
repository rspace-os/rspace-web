package com.researchspace.model.dto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO object that contain user's settings for particular integration (or 'App')
 */
@Getter
@Setter
@EqualsAndHashCode(of="name")
public class IntegrationInfo implements Serializable {

	private static final long serialVersionUID = -5497310525279282441L;
	
	/**
	 * From integration name, gets App name
	 * @param integrationName
	 * @return
	 */
	public static String getAppNameFromIntegrationName(String integrationName) {
		return "app." + integrationName.toLowerCase();
	}

	private String name;
	// the 'label' property of the App
	private String displayName;

	/**
	 * Whether the sysadmin has set the x.enabled property to make this
	 * integration available to users
	 * 
	 * @return
	 */
	private boolean available;
	
	/**
	 * Boolean test for whether the user has enabled this Integration for
	 * themselves
	 * 
	 * @return
	 */
	private boolean enabled;
	
	/**
	 * For integrations maintaining server-side OAauth credentials, stores whether
	 *  credential is stored.
	 */
	private boolean oauthConnected = false;

	/*
	 * key is an id of option element set, value is a map of option names and
	 * values within the set
	 */
	private Map<String, Object> options = new HashMap<>();
	
	/** 
	 * Check whether the App has any options set up. For configurable applications (e.g. Github, Slack) 
	 * that is equivalent with app having some configurations.
	 * @return
	 */
	public boolean hasOptions() {
	    return !options.isEmpty();
	}

	/** Retrieve first optionsId. Useful if App has only one option set.<br>
	 *  Returns <code>null</code> if app has no options
	 */
	public String retrieveFirstOptionsId() {
		if (options.isEmpty()) {
			return null;
		}
		return (String) options.keySet().iterator().next();
	}

	/** Retrieve first option value. Useful if App has only one option.<br>
	 * Returns <code>null</code> if app has no options 
	 */
	@SuppressWarnings("unchecked")
	public String retrieveFirstOptionValue() {
		if (options.isEmpty()) {
			return null;
		}
		String firstOptionValue = null;
		Map<String, String> firstOptionSet = (Map<String, String>) options.values().iterator().next();
		if (firstOptionSet != null && !firstOptionSet.isEmpty()) {
			firstOptionValue = firstOptionSet.values().iterator().next();
		}
		return firstOptionValue;
	}

	/**
	 * For an integration where an OAUth access token is stored, this method returns {@code}true{@code}
	 * if the app is available, enabled and an access token is stored.
	 * @return
	 */
	@JsonIgnore
	public boolean isOAuthAppUsable(){
		return isAvailable() && isEnabled() && isOauthConnected();
	}

}
