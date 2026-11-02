package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.booking.service.BookingDisplayPreferencesManager.ResolvedBookingDisplayPreferences;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.BookingNotificationData;
import com.researchspace.service.JsonMessageSource;
import java.time.ZoneId;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class BookingNotificationMessageFormatterTest {

  private final BookingNotificationMessageFormatter formatter =
      new BookingNotificationMessageFormatter(new JsonMessageSource());

  @Test
  void sameDayRangesNameTheDateOnceAndOvernightRangesKeepBothDates() {
    BookingNotificationData data = data("2026-11-06T15:00:00Z", "2026-11-06T16:00:00Z");
    String sameDay =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED, data, ZoneId.of("UTC"), Locale.US);
    assertEquals(1, sameDay.split("Nov 6, 2026", -1).length - 1, sameDay);
    assertTrue(sameDay.contains("to 4:00 PM (UTC, UTC)"), sameDay);
    data.setEndTime("2026-11-07T16:00:00Z");
    String overnight =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED, data, ZoneId.of("UTC"), Locale.US);
    assertTrue(overnight.contains("Nov 6, 2026"), overnight);
    assertTrue(overnight.contains("Nov 7, 2026"), overnight);
  }

  @Test
  void dateElisionUsesTheDisplayZoneRatherThanUtc() {
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    String sameDay =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED,
            data("2026-11-06T23:00:00Z", "2026-11-07T01:00:00Z"),
            berlin,
            Locale.US);
    assertEquals(1, sameDay.split("Nov 7, 2026", -1).length - 1, sameDay);
    assertFalse(sameDay.contains("Nov 6, 2026"), sameDay);
    String overnight =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED,
            data("2026-11-06T22:00:00Z", "2026-11-06T23:00:00Z"),
            berlin,
            Locale.US);
    assertTrue(overnight.contains("Nov 6, 2026"), overnight);
    assertTrue(overnight.contains("Nov 7, 2026"), overnight);
  }

  @Test
  void resolvesBrowserInstitutionAndCustomDisplayZones() {
    ZoneId browser = ZoneId.of("Asia/Tokyo");
    ZoneId institution = ZoneId.of("Europe/Berlin");

    assertEquals(
        ZoneId.of("America/New_York"),
        BookingNotificationMessageFormatter.zoneFor(
            preferences(BookingTimezoneMode.CUSTOM, "America/New_York", "Europe/Berlin"),
            browser,
            institution));
    assertEquals(
        institution,
        BookingNotificationMessageFormatter.zoneFor(
            preferences(BookingTimezoneMode.INSTITUTION, null, "Europe/Berlin"),
            browser,
            ZoneId.of("UTC")));
    assertEquals(
        browser,
        BookingNotificationMessageFormatter.zoneFor(
            preferences(BookingTimezoneMode.BROWSER, null, "Europe/Berlin"), browser, institution));
    assertEquals(
        institution,
        BookingNotificationMessageFormatter.zoneFor(
            preferences(BookingTimezoneMode.BROWSER, null, "Europe/Berlin"), null, institution));
  }

  @Test
  void formatsBothOccurrencesOfTheDstOverlapWithTheirOffsets() {
    BookingNotificationData data = data("2026-11-01T05:30:00Z", "2026-11-01T06:30:00Z");

    String message =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED,
            data,
            ZoneId.of("America/New_York"),
            Locale.US);

    assertTrue(message.contains("Nov 1, 2026, 1:30 AM (America/New_York, UTC-04:00)"));
    assertTrue(message.contains("to 1:30 AM (America/New_York, UTC-05:00)"));
  }

  @Test
  void notificationDataRoundTripsThroughTheExistingJsonColumn() {
    Notification notification = new Notification();
    notification.setNotificationType(NotificationType.NOTIFICATION_BOOKING_CANCELLED);
    notification.setNotificationDataObject(data("2026-01-02T03:04:05Z", "2026-01-02T04:04:05Z"));

    BookingNotificationData restored =
        assertInstanceOf(BookingNotificationData.class, notification.getNotificationDataObject());
    assertEquals("42", restored.getBookingId());
    assertEquals("Microscope", restored.getInstrumentName());
  }

  private static ResolvedBookingDisplayPreferences preferences(
      BookingTimezoneMode mode, String customTimezone, String institutionTimezone) {
    return new ResolvedBookingDisplayPreferences(
        "08:00", "18:00", mode, customTimezone, institutionTimezone, true);
  }

  private static BookingNotificationData data(String start, String end) {
    BookingNotificationData data = new BookingNotificationData();
    data.setBookingId("42");
    data.setInstrumentName("Microscope");
    data.setInstrumentGlobalIdentifier("IN12");
    data.setStartTime(start);
    data.setEndTime(end);
    return data;
  }
}
