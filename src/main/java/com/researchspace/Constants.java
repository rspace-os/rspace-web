package com.researchspace;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Constant values used throughout the application.
 */

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constants {

	/**
	 * The name of the ResourceBundle used in this application
	 */
	public static final String BUNDLE_KEY = "bundles.ApplicationResources";

	/**
	 * File separator from System properties
	 */
	public static final String FILE_SEP = System.getProperty("file.separator");

	/**
	 * The name of the configuration hashmap stored in application scope.
	 */
	public static final String CONFIG = "appConfig";

	/**
	 * Session scope attribute that holds the locale set by the user. By setting
	 * this key to the same one that Struts uses, we get synchronization in
	 * Struts w/o having to do extra work or have two session-level variables.
	 */
	public static final String PREFERRED_LOCALE_KEY = "org.apache.struts2.action.LOCALE";

	/**
	 * The name of the Administrator role
	 */
	public static final String ADMIN_ROLE = "ROLE_ADMIN";

	/**
	 * The name of the Administrator role
	 */
	public static final String SYSADMIN_ROLE = "ROLE_SYSADMIN";

	/**
	 * The name of the User role
	 */
	public static final String USER_ROLE = "ROLE_USER";

	/**
	 * The name of the PI role
	 */
	public static final String PI_ROLE = "ROLE_PI";

	/**
	 * The name of the Group Owner role
	 */
	public static final String GROUP_OWNER_ROLE = "ROLE_GROUP_OWNER";


	/**
	 * The name of the Anonymous role
	 */
	public static final String ANONYMOUS_ROLE = "ROLE_ANONYMOUS";

	/**
	 * The name of the CSS Theme setting.
	 */
	public static final String CSS_THEME = "csstheme";

	/**
	 * Defaul sysadmin user name
	 */
	public static final String SYSADMIN_UNAME = "sysadmin1";

	/**
	 * Internal system admin used by liquibase
	 */
	public static final String RS_LIQUIBASE_ADMIN = "rsliquibaseadmin1";
}
