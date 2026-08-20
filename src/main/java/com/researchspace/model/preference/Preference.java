package com.researchspace.model.preference;

import com.researchspace.model.comms.NotificationType;

/**
 * Contains hard-coded information about preferences.
 * By convention, these preferences about notifications are named after the NotificiationType .<br>
 * This ordering of the enums should be maintained; the ordinal value is stored in the database.
 * I.e. <strong> don't rearrange the order in which these preferences are defined!<br/>
 * New preferences must be appended to the end of the enum definition</strong>
 */
public enum Preference {
	
	/** Follows naming convention of {@link NotificationType} name + "_PREF" */
	NOTIFICATION_DOCUMENT_SHARED_PREF(Boolean.TRUE.toString(), SettingsType.BOOLEAN,
			PreferenceCategory.MESSAGING, "A document is shared with me"),
	
	/** Follows naming convention of {@link NotificationType} name + "_PREF" */
	NOTIFICATION_DOCUMENT_EDITED_PREF(Boolean.TRUE.toString(), SettingsType.BOOLEAN,
			PreferenceCategory.MESSAGING, "A shared document is edited"),

	/** Follows naming convention of {@link NotificationType} name + "_PREF" */
	NOTIFICATION_DOCUMENT_UNSHARED_PREF(Boolean.TRUE.toString(), SettingsType.BOOLEAN,
			PreferenceCategory.MESSAGING, "A document is unshared with me"),
	
	/** Follows naming convention of {@link NotificationType} name + "_PREF" */
	NOTIFICATION_REQUEST_STATUS_CHANGE_PREF(Boolean.TRUE.toString(), SettingsType.BOOLEAN,
			PreferenceCategory.MESSAGING, "A request status changes"),

	/** Follows naming convention of {@link NotificationType} name + "_PREF" */
	PROCESS_COMPLETED_PREF(Boolean.TRUE.toString(), SettingsType.BOOLEAN,
			PreferenceCategory.MESSAGING, "A background process finishes"),

	/** Follows naming convention of {@link NotificationType} name + "_PREF" */
	PROCESS_FAILED_PREF(Boolean.TRUE.toString(), SettingsType.BOOLEAN,
			PreferenceCategory.MESSAGING, "A background process fails"),

	/** Preference for receiving notifications by email; boolean type */
	BROADCAST_NOTIFICATIONS_BY_EMAIL(Boolean.TRUE.toString(), SettingsType.BOOLEAN,
			PreferenceCategory.MESSAGING_BROADCAST, "Send notifications by email"),

	/** Preference for receiving messages and requests by email; boolean type */
	BROADCAST_REQUEST_BY_EMAIL(Boolean.TRUE.toString(), SettingsType.BOOLEAN,
			PreferenceCategory.MESSAGING_BROADCAST, "Send requests by email"),

	/**
	 * Preference for connecting to an eCAT server; string type
	 * @deprecated ECAT integration has been removed. Need to keep this in place though to maintain ordinal value
	 * which is used in the database.
	 * */
	@Deprecated
	ECAT_SERVER("", SettingsType.STRING,
			PreferenceCategory.EXTERNAL_DATA, "eCAT Server"),
	
	/** Page size to be used on Export */
	UI_PDF_PAGE_SIZE(ExportPageSize.UNKNOWN.toString(), SettingsType.ENUM, 
			 PreferenceCategory.UI, "Default PDF page size", new EnumPreferenceValidator(ExportPageSize.class)),

	/** User enablement of Box linking */
	BOX(Boolean.FALSE.toString(), SettingsType.BOOLEAN, PreferenceCategory.INTEGRATIONS, "Box"),
	
	/** User enablement of Googledrive linking */
	GOOGLEDRIVE(Boolean.FALSE.toString(), SettingsType.BOOLEAN, PreferenceCategory.INTEGRATIONS, "GoogleDrive"),

	/** User enablement of Onedrive linking */
	ONEDRIVE(Boolean.FALSE.toString(), SettingsType.BOOLEAN, PreferenceCategory.INTEGRATIONS, "OneDrive"),
	
	/** User enablement of Dropbox linking */
	DROPBOX(Boolean.FALSE.toString(), SettingsType.BOOLEAN, PreferenceCategory.INTEGRATIONS, "Dropbox"),

	/** User enablement of Chemisrty search /structure */
	CHEMISTRY(Boolean.FALSE.toString(), SettingsType.BOOLEAN, PreferenceCategory.INTEGRATIONS, "Chemistry"),
	
	/** User enablement of Mendeley linking
	 * @deprecated Mendeley-linking integration has been removed. Need to keep this in place though to maintain 
  	 * ordinal value which is used in the database.
         * */
	@Deprecated
	MENDELEY(Boolean.FALSE.toString(), SettingsType.BOOLEAN, PreferenceCategory.INTEGRATIONS, "Mendeley"),

	/** User enablement of Ecat linking
	 * @deprecated ECAT integration has been removed. Need to keep this in place though to maintain ordinal value
	 * which is used in the database.
	 * */
	@Deprecated
	ECAT(Boolean.FALSE.toString(), SettingsType.BOOLEAN, PreferenceCategory.INTEGRATIONS, "Ecat"),

	/** User preference about type of Box links inserted into documents */
	BOX_LINK_TYPE(BoxLinkType.LIVE.toString(), SettingsType.ENUM, 
			PreferenceCategory.INTEGRATIONS, "Box Link Type", new EnumPreferenceValidator(BoxLinkType.class)),

	/** User preference whether to show Chameleon on-boarding tours (default is FALSE, however, for new users it is set
	 *  to TRUE automatically) */
	CHAMELEON_TOURS_ENABLED_FOR_USER(Boolean.FALSE.toString(), SettingsType.BOOLEAN, PreferenceCategory.UI,
			"Show guided tours"),

    WORKSPACE_RESULTS_PER_PAGE("10", SettingsType.NUMBER, PreferenceCategory.UI,
            "Number of Workspace items per page"),

	PI_CAN_EDIT_ALL_WORK_IN_LABGROUP(HierarchicalPermission.DENIED_BY_DEFAULT.toString(), SettingsType.ENUM, PreferenceCategory.UI,
			"PI can edit all work in lab group"),
	
	NOTIFICATION_DOCUMENT_DELETED_PREF(Boolean.TRUE.toString(), SettingsType.BOOLEAN,
			PreferenceCategory.MESSAGING, "A shared document is deleted"),
	
	/** User preference holding the list of UI settings that generally don't impact server side, 
	 *  e.g. visibility toggles. Stored as a comma-separated name=value string. */
	UI_CLIENT_SETTINGS("", SettingsType.STRING, PreferenceCategory.UI, "UI client-side settings"),
	
	// these 3 are persistent preferences for items per page
	FORM_RESULTS_PER_PAGE("10", SettingsType.NUMBER, PreferenceCategory.UI,
            "Number of Forms per page"),
	
	DIRECTORY_RESULTS_PER_PAGE("10", SettingsType.NUMBER, PreferenceCategory.UI,
            "Number of Directory items per page"),
	
	SHARED_RECORDS_RESULTS_PER_PAGE("10", SettingsType.NUMBER, PreferenceCategory.UI,
            "Number of shared items per page"),

	CURRENT_WORKSPACE_VIEW_MODE("LIST_VIEW", SettingsType.STRING, PreferenceCategory.UI,
			"Whether Workspace page opens up as list view or tree view initially"),
	DELETED_RECORDS_RESULTS_PER_PAGE("10", SettingsType.NUMBER, PreferenceCategory.UI,
			"Number of deleted items per page"),

	/** Similar to UI_CLIENT_SETTINGS, but for storing arbitrary data in json key-value format  */
	UI_JSON_SETTINGS("", SettingsType.TEXT, PreferenceCategory.UI, "UI client-side settings, in json format");

	private String defaultValue;
	private SettingsType prefType;
	private PreferenceCategory category;
	private String displayMessage;
	private PreferenceValidator prefValidator;

	private Preference(String defaultValue, SettingsType prefType, PreferenceCategory category,
			String displayMessage) {
		this.defaultValue = defaultValue;
		this.prefType = prefType;
		this.category = category;
		this.displayMessage = displayMessage;
		this.prefValidator = PreferenceValidator.ALWAYS_TRUE;
	}

	private Preference(String defaultValue, SettingsType prefType, PreferenceCategory category,
			String displayMessage, PreferenceValidator validator) {
		this(defaultValue, prefType, category, displayMessage);
		this.prefValidator = validator;
	}
	
	/**
	 * If <code>value</code> is invalid, returns an error message. O
	 * @param value
	 * @return An error string, or <code>null</code> if is not an invalid value.
	 */
	public String getInvalidErrorMessageForValue(String value) {
		return prefValidator.getMsgIfInvalid(value);
	}
	
	/**
	 * Boolean test as to whether the supplied value is valid for this preference
	 * @param value
	 * @return <code>true</code> if a valid value, <code>false</code> otherwise
	 */
	public boolean isValid(String value) {
		return getInvalidErrorMessageForValue(value) == null;
	}

	/**
	 * Gets a user-friendly string explaining the preference
	 * 
	 * @return
	 */
	public String getDisplayMessage() {
		return displayMessage;
	}

	/**
	 * Gets a string representation of the default value for the Preference
	 * 
	 * @return
	 */
	public String getDefaultValue() {
		return defaultValue;
	}

	void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	/**
	 * Gets the category of Preference
	 * @return
	 */
	public PreferenceCategory getCategory() {
		return category;
	}

	/**
	 * Gets the type: One of boolean, string or number.
	 * @return
	 */
	public SettingsType getPrefType() {
		return prefType;
	}

	/**
	 * Boolean test as to whether a preference is for messaging related activities.
	 * See RSPAC-2007.
	 */
	public boolean isMessagingPreference() {
		return PreferenceCategory.MESSAGING.equals(getCategory())
		|| PreferenceCategory.MESSAGING_BROADCAST.equals(getCategory());
	}
}
