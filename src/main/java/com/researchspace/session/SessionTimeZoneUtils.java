package com.researchspace.session;

import java.util.Date;
import java.util.TimeZone;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

public class SessionTimeZoneUtils {
	
	/**
	 * Converts a server UTC time into user local time so long as there is a session attribute
	 * called {@link SessionAttributeUtils#TIMEZONE} holding a java.util,Timezone object. This will
	 * have been set in at user login time.
	 * 
	 * @param serverUtcTime
	 * @return A {@link DateTime} object in the user's local time, or UTC if the user's local time
	 *         zone was missing or unrecognized.
	 */
	public DateTime convertToLocalTimeZone(Date serverUtcTime) {
		DateTimeZone tz = getUserTimezone();
		DateTime inputDt = new DateTime(serverUtcTime, DateTimeZone.UTC);
		return inputDt.toDateTime(tz);
	}

	/**
	 * Converts an incoming user local time to server UTC time. <b>Does not yet work properly</b>
	 * 
	 * @param userLocalTime
	 * @return A {@link DateTime} of the current time
	 */
	public DateTime convertToUTCTimeZone(Date userLocalTime) {
		DateTimeZone tz = getUserTimezone();
		DateTime inputDt = new DateTime(userLocalTime, tz);
		return inputDt.toDateTime(tz);
	}

	DateTimeZone getUserTimezone() {
		try {
			Session httpsession = SecurityUtils.getSubject().getSession();
			TimeZone tz = (TimeZone) httpsession.getAttribute(SessionAttributeUtils.TIMEZONE);
			if (tz != null) {
				return DateTimeZone.forTimeZone(tz);
			} else {
				return DateTimeZone.getDefault();
			}

		} catch (Exception e) {
			return DateTimeZone.getDefault();
		}
	}

	/**
	 * As {@link #convertToLocalTimeZone(Date)} but also formats the date  in a default manner.
	 * @param serverUTCdate
	 *            The server time
	 * @return A String in the user's local time in ISO-8601 date format , or UTC if the user's local time zone was missing
	 *         or unrecognized.
	 */
	public String formatDateForClient(Date serverUTCdate) {
		return DateTimeFormat.forPattern("yyyy-MM-dd").print(convertToLocalTimeZone(serverUTCdate));
	}
	
	/**
	 * As {@link #convertToLocalTimeZone(Date)} but also formats the datetime in a default manner to minute resolution
	 * @param serverUTCdate
	 *            The server time
	 * @return A String in the user's local time in ISO-8601 date-time format, or UTC if the user's local time zone was missing
	 *         or unrecognized.
	 */
	public String formatDateTimeForClient(Date serverUTCdate) {
		return formatDateTimeForClient(serverUTCdate, false, false);
	}
	
	/**
	 * As {@link #convertToLocalTimeZone(Date)} but also formats the datetime in a default manner.
	 * @param serverUTCdate
	 *            The server time
	 * @param showSeconds Whether to show seconds or not
	 * @param showTimezone whether to show timezone or not.
	 * @return A String in the user's local time in ISO-8601 date-time format, or UTC if the user's local time zone was missing
	 *         or unrecognized.
	 */
	public String formatDateTimeForClient(Date serverUTCdate, boolean showSeconds, boolean showTimezone) {
		String format = "yyyy-MM-dd HH:mm";
		if(showSeconds) {
			format = format + ":ss";
		}
		if(showTimezone) {
			format = format + " Z";
		}
		return DateTimeFormat.forPattern(format).print(convertToLocalTimeZone(serverUTCdate));
	}

}
