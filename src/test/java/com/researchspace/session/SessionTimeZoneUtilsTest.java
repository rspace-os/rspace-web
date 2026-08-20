package com.researchspace.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

public class SessionTimeZoneUtilsTest {
	SessionTimeZoneUtilsTSS dateUtiltss;

	private class SessionTimeZoneUtilsTSS extends SessionTimeZoneUtils {
		DateTimeZone tz;

		// override session mechanism
		DateTimeZone getUserTimezone() {
			return tz;
		}
	}

	@Before
	public void setup() {
		dateUtiltss = new SessionTimeZoneUtilsTSS();
	}

	@Test
	public void testconvertToLocalTimeZone() {

		TimeZone localTz = TimeZone.getTimeZone("Asia/Irkutsk");

		// mimic HTTP Session
		dateUtiltss.tz = DateTimeZone.forTimeZone(localTz);

		TimeZone utctz = TimeZone.getTimeZone("UTC");
		Calendar cal = Calendar.getInstance();
		cal.setTimeZone(utctz);
		Date utc = cal.getTime();

		DateTime local = dateUtiltss.convertToLocalTimeZone(utc);
		assertEquals(local.getZone(), DateTimeZone.forTimeZone(localTz));
	}

	@Test
	public void formatDateTimeForClient() {
		Date date = new Date();
		String formatDate = dateUtiltss.formatDateTimeForClient(date, true, true);
		assertShowsSeconds(formatDate);
		assertTrue(showsTimezone(formatDate));

		formatDate = dateUtiltss.formatDateTimeForClient(date, true, false);
		assertShowsSeconds(formatDate);
		assertFalse(showsTimezone(formatDate));

		formatDate = dateUtiltss.formatDateTimeForClient(date, false, true);
		assertNoSeconds(formatDate);
		assertTrue(showsTimezone(formatDate));

		formatDate = dateUtiltss.formatDateTimeForClient(date, false, false);
		assertNoSeconds(formatDate);
		assertFalse(showsTimezone(formatDate));
	}

	private void assertShowsSeconds(String formatDate) {
		assertEquals(3, formatDate.split(":").length);
	}

	private void assertNoSeconds(String formatDate) {
		assertEquals(2, formatDate.split(":").length);
	}

	private boolean showsTimezone(String formatDate) {
		return formatDate.matches(".+\\+\\d{4}");
	}

}
