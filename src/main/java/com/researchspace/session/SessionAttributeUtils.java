package com.researchspace.session;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.joda.time.DateTimeZone;

import com.researchspace.model.User;

/**
 * Class to hold names & static methods for session attributes in one place to avoid
 * confusion.
 */
public class SessionAttributeUtils {
	
	/**
	 * Holds a reference to a {@link User} object.
	 */
	public static final String USER = "com_researchspace_User";
	
	/**
	 * 
	 */
	public static final String USER_INFO = "userInfo";
	
	/**
	 * 
	 */
	public static final String ANALYTICS_USER_ID = "analyticsUserId";
	
	/**
	 * 
	 */
	public static final String TIMEZONE = "com_rs_timezone";
	
	/**
	 * Attribute name used to hold id remote user is set?
	 */
	public static final String REMOTE_USER_STATUS = "REMOTE_USER_STATUS";
	
	/**
	 * YES value for REMOTE_USER_STATUS
	 */
	public static final String REMOTE_USER_ATTR_YES_VALUE = "YES";

	/**
	 * Name for session attribute as to whether user is operating 
	 *  as another user
	 */
	public static final String IS_RUN_AS = "rs.IS_RUN_AS";
	
	/**
	 * Name for session attribute as to whether this is the 1st request in the session
	 */
	public static final String FIRST_REQUEST = "firstRequest";
	
	/**
	 * Whether this is the first time user logged in since their account was created  
	 */
	public static final String FIRST_LOGIN = "firstLogin";
	
	/**
	 * Whether initialisation that is supposed to happen after the first time user 
	 * logs in to their account was completed  
	 */
	public static final String FIRST_LOGIN_HANDLED = "firstLoginHandled";
	/**
	 * Boolean value for whether onboarding has been handled
	 */
	public static final String ONBOARDING_HANDLED = "onboardingHandled";
	
	/**
	 * Boolean value for whether optional custom example content has been added
	 */
	public static final String EXAMPLECONTENT_HANDLED = "exampleContentHandled";

	/** 
	 * Whether external filestore connection is set up and working for the user. 
	 */
	public static final String EXT_FILESTORE_CONNECTION_OK = "extFilestoreConnectionOK";
	
	/**
	 * Stores latest import result.
	 */
	public static final String LATEST_IMPORT_REPORT = "rs.importResult";
	
	/**
	 * Key for proggress monitor to store message about status of batch user registration 
	 */
	public static final String BATCH_REGISTRATION_PROGRESS = "rs-userBatchRegistration";

	/**
	 * Key for proggress monitor  about status of batch word import
	 */
	public static final String BATCH_WORDIMPORT_PROGRESS = "rs-wordImporter";
	
	/**
	 * Key for progress monitor  about status of archive import
	 */
	public static final String RS_IMPORT_XML_ARCHIVE_PROGRESS = "rs-importXMLArchive";
	
	/**
	 * Key for progress monitor  about status of archive import
	 */
	public static final String RS_DELETE_RECORD_PROGRESS = "rs-deleteRecord";
	
	/**
	 * Stores 'state' parameter of OAUth2 grant flow to ensure integrity of incoming callback
	 */
	public static final String RS_OAUTH_STATE = "rs-oauthState";
	
	/**
	 * A text encryptor/decryptor using key derived from user's password.
	 */
	 String RS_TEXT_ENCRYPTOR = "rs-textEncryptor";
	
	 /**
	  * Name of session attribute for Egnyte session token
	  */
	 public static final String SESSION_EGNYTE_TOKEN = "SESSION_EGNYTE_TOKEN";
	 
	 /**
	  * Name of session variable for RSpace version string.
	  */
	 public static final String RSPACE_VERSION = "rspaceVersion";
	
	/**
	 * Get timezone from the string value of the TIMEZONE session attribute. Will be
	 *  UTC if this is null or not recognised.
	 * @param attvalue A standard Olsen timezone string.
	 * @return A JodaTime {@link DateTimeZone}
	 */
	public static DateTimeZone getTimeZoneFromSessionAttribute(String attvalue) {
		if (StringUtils.isBlank(attvalue)) {
			return DateTimeZone.UTC;
		}
		return DateTimeZone.forID(attvalue);
	}

	/**
	 * Sets new session attribute value, overwriting any previous value.
	 * @param name
	 * @param value
	 */
	public static void setSessionAttribute(String name, Object value) {
		SecurityUtils.getSubject().getSession().setAttribute(name, value);
	}

	/**
	 * Gets session attribute
	 * @param name
	 * @return the attribute or <code>null</code>
	 */
	public static Object getSessionAttribute(String name) {
		return SecurityUtils.getSubject().getSession().getAttribute(name);
	}
	/**
	 * Removes session attribute
	 * @param name
	 * @return the removed attribute or <code>null</code> if there is no attribute of this name.
	 */
	public static Object removeSessionAttribute(String name) {
		return SecurityUtils.getSubject().getSession().removeAttribute(name);
	}
	
	/**
	 * Returns Web sessionID, if there is one. If session is not created will throw NPE
	 * @return The Session ID.
	 * @throws NullPointerException if there is no session
	 * 
	 */
	public static String getSessionId() {
		return SecurityUtils.getSubject().getSession(false).getId().toString();
	}

}
