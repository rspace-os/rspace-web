package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.booking.service.BookingDisplayPreferencesManager.ResolvedBookingDisplayPreferences;
import com.researchspace.model.booking.BookingTimeFormat;
import com.researchspace.model.booking.BookingTimezoneMode;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.BookingNotificationData;
import com.researchspace.service.JsonMessageSource;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class BookingNotificationMessageFormatterTest {

  private static final String OVERLAP_START = "2026-11-01T05:30:00Z";
  private static final String OVERLAP_END = "2026-11-01T06:30:00Z";
  private static final String REGIONAL_START = "2026-10-08T04:00:00Z";
  private static final String REGIONAL_END = "2026-10-08T05:00:00Z";

  private final BookingNotificationMessageFormatter formatter =
      new BookingNotificationMessageFormatter(
          new JsonMessageSource(), "https://rspace.example.org//");

  @Test
  void sameDayRangesNameTheDateOnceInNoticesAndEmail() {
    BookingNotificationData data = data("2026-11-06T15:00:00Z", "2026-11-06T16:00:00Z");
    for (String message :
        List.of(
            formatter.format(
                NotificationType.NOTIFICATION_BOOKING_CREATED, data, ZoneId.of("UTC"), Locale.US),
            formatter.formatForEmail(
                NotificationType.NOTIFICATION_BOOKING_CREATED,
                data,
                ZoneId.of("UTC"),
                Locale.US))) {
      assertEquals(1, message.split("Nov 6, 2026", -1).length - 1, message);
      assertTrue(message.contains("4:00 PM"), message);
    }
    data.setEndTime("2026-11-07T16:00:00Z");
    String overnight =
        formatter.formatForEmail(
            NotificationType.NOTIFICATION_BOOKING_CREATED, data, ZoneId.of("UTC"), Locale.US);
    assertTrue(overnight.contains("Nov 6, 2026"), overnight);
    assertTrue(overnight.contains("Nov 7, 2026"), overnight);
  }

  @Test
  void dateElisionUsesTheDisplayZoneRatherThanUtc() {
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    for (boolean email : new boolean[] {false, true}) {
      BookingNotificationData sameDay = data("2026-11-06T23:00:00Z", "2026-11-07T01:00:00Z");
      BookingNotificationData overnight = data("2026-11-06T22:00:00Z", "2026-11-06T23:00:00Z");
      String sameDayMessage =
          email
              ? formatter.formatForEmail(
                  NotificationType.NOTIFICATION_BOOKING_CREATED, sameDay, berlin, Locale.US)
              : formatter.format(
                  NotificationType.NOTIFICATION_BOOKING_CREATED, sameDay, berlin, Locale.US);
      String overnightMessage =
          email
              ? formatter.formatForEmail(
                  NotificationType.NOTIFICATION_BOOKING_CREATED, overnight, berlin, Locale.US)
              : formatter.format(
                  NotificationType.NOTIFICATION_BOOKING_CREATED, overnight, berlin, Locale.US);
      assertEquals(1, sameDayMessage.split("Nov 7, 2026", -1).length - 1, sameDayMessage);
      assertFalse(sameDayMessage.contains("Nov 6, 2026"), sameDayMessage);
      assertTrue(overnightMessage.contains("Nov 6, 2026"), overnightMessage);
      assertTrue(overnightMessage.contains("Nov 7, 2026"), overnightMessage);
    }
  }

  @Test
  void changedMessagesIncludeBothIntervalsInTheRecipientsClockAndZone() {
    BookingNotificationData data = data("2026-11-09T11:00:00Z", "2026-11-09T12:00:00Z");
    data.setPreviousStartTime("2026-11-09T10:00:00Z");
    data.setPreviousEndTime("2026-11-09T11:00:00Z");
    String message =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CHANGED,
            data,
            ZoneId.of("Europe/London"),
            Locale.UK,
            Locale.UK,
            BookingTimeFormat.H24);
    assertTrue(message.contains("was changed from"), message);
    assertTrue(message.contains("10:00 GMT (Europe/London)"), message);
    assertTrue(message.contains("12:00 GMT (Europe/London)"), message);
    String email =
        formatter.formatForEmail(
            NotificationType.NOTIFICATION_BOOKING_CHANGED,
            data,
            ZoneId.of("Europe/London"),
            Locale.UK,
            BookingTimeFormat.H24);
    assertTrue(email.contains("was changed from"), email);
    assertTrue(email.contains("10:00 GMT (Europe/London)"), email);
    assertTrue(email.contains("12:00 GMT (Europe/London)"), email);
  }

  @Test
  void identifiesTheLondonZoneInWinterAndSummer() {
    for (String[] example :
        new String[][] {
          {"2026-01-02T13:00:00Z", "13:00 GMT (Europe/London)"},
          {"2026-07-02T12:00:00Z", "13:00 BST (Europe/London)"}
        }) {
      String message =
          formatter.formatForEmail(
              NotificationType.NOTIFICATION_BOOKING_CREATED,
              data(example[0], example[0]),
              ZoneId.of("Europe/London"),
              Locale.UK,
              BookingTimeFormat.H24);
      assertTrue(message.contains(example[1]), message);
    }
  }

  @Test
  void recognisesOnlyAbsoluteHttpLinkBasesForEmail() {
    assertTrue(BookingNotificationMessageFormatter.isAbsoluteHttpUrl("https://rspace.example.org"));
    assertTrue(
        BookingNotificationMessageFormatter.isAbsoluteHttpUrl("http://localhost:8080/rspace"));
    assertFalse(BookingNotificationMessageFormatter.isAbsoluteHttpUrl(""));
    assertFalse(BookingNotificationMessageFormatter.isAbsoluteHttpUrl("rspace.example.org"));
    assertFalse(BookingNotificationMessageFormatter.isAbsoluteHttpUrl("/booking"));
    assertFalse(BookingNotificationMessageFormatter.isAbsoluteHttpUrl("ftp://rspace.example.org"));
    assertFalse(BookingNotificationMessageFormatter.isAbsoluteHttpUrl("https:///no-host"));
    assertFalse(BookingNotificationMessageFormatter.isAbsoluteHttpUrl("https://bad host"));
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
    BookingNotificationData data = data(OVERLAP_START, OVERLAP_END);

    String message =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED,
            data,
            ZoneId.of("America/New_York"),
            Locale.US);

    assertTrue(message.contains("Nov 1, 2026, 1:30 AM EDT (America/New_York)"));
    assertTrue(message.contains("1:30 AM EST (America/New_York)"));
  }

  @Test
  void inAppMessagesLinkTheBookingAndItemWithRootRelativeUrlsAndEscapedValues() {
    BookingNotificationData data = data(OVERLAP_START, OVERLAP_END);
    data.setInstrumentName("<script>alert(\"x\")</script> & scope");

    String message =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CANCELLED, data, ZoneId.of("UTC"), Locale.US);

    assertTrue(
        message.startsWith(
            "Booking <a href=\"/booking/calendar/bookings/42\">42</a> for instrument"
                + " <a href=\"/booking/bookable-items/IN12\">&lt;script&gt;alert(&quot;x&quot;)"
                + "&lt;/script&gt; &amp; scope (IN12)</a> was cancelled."),
        message);
  }

  @Test
  void emailMessagesUseAbsoluteLinksFromTheServerUrl() {
    BookingNotificationData data = data(OVERLAP_START, OVERLAP_END);

    String message =
        formatter.formatForEmail(
            NotificationType.NOTIFICATION_BOOKING_CREATED, data, ZoneId.of("UTC"), Locale.US);

    assertTrue(
        message.startsWith(
            "Booking <a href=\"https://rspace.example.org/booking/calendar/bookings/42\">42</a>"
                + " was created for instrument"
                + " <a href=\"https://rspace.example.org/booking/bookable-items/IN12\">"
                + "Microscope (IN12)</a> from "),
        message);
  }

  @Test
  void aRestoredBookingSaysRestoredRatherThanCreated() {
    BookingNotificationData data = data("2026-11-01T05:30:00Z", "2026-11-01T06:30:00Z");
    data.setRestored(true);

    String message =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED, data, ZoneId.of("UTC"), Locale.US);

    assertTrue(
        message.startsWith(
            "Booking <a href=\"/booking/calendar/bookings/42\">42</a> for instrument"
                + " <a href=\"/booking/bookable-items/IN12\">Microscope (IN12)</a> was restored."
                + " It is scheduled from "),
        message);
  }

  @Test
  void linkTargetsEncodeAndEscapeTheirIdentifiers() {
    BookingNotificationData data = data(OVERLAP_START, OVERLAP_END);
    data.setBookingId("42\"><b>");
    data.setInstrumentGlobalIdentifier("IN12/../x?y");

    String message =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED, data, ZoneId.of("UTC"), Locale.US);

    assertTrue(message.contains("href=\"/booking/calendar/bookings/42%22%3E%3Cb%3E\""), message);
    assertTrue(message.contains("href=\"/booking/bookable-items/IN12%2F..%2Fx%3Fy\""), message);
  }

  @Test
  void writesTimesWithTheRegionalClockButTheAppLanguagesWordsAndFieldOrder() {
    BookingNotificationData data = data(REGIONAL_START, REGIONAL_END);
    ZoneId berlin = ZoneId.of("Europe/Berlin");

    String british =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED,
            data,
            berlin,
            Locale.US,
            Locale.forLanguageTag("en-GB"));
    String american =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED, data, berlin, Locale.US, Locale.US);

    assertTrue(british.contains("Oct 8, 2026, 06:00 CEST (Europe/Berlin)"), british);
    assertTrue(british.contains("07:00 CEST (Europe/Berlin)"), british);
    // ICU writes a narrow no-break space before the day period, as browsers do.
    assertTrue(
        normalised(american).contains("Oct 8, 2026, 6:00 AM CEST (Europe/Berlin)"), american);
    assertFalse(american.contains("06:00"), american);
  }

  @Test
  void withoutARegionalLocaleFormatsExactlyAsBefore() {
    BookingNotificationData data = data(REGIONAL_START, REGIONAL_END);
    ZoneId berlin = ZoneId.of("Europe/Berlin");

    assertEquals(
        formatter.format(NotificationType.NOTIFICATION_BOOKING_CREATED, data, berlin, Locale.US),
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CREATED, data, berlin, Locale.US, null));
  }

  @Test
  void emailMessagesUseTheRecipientsExplicitClockAndOtherwiseTheLanguagesUsualClock() {
    BookingNotificationData data = data("2026-10-08T04:00:00Z", "2026-10-08T05:00:00Z");
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    NotificationType created = NotificationType.NOTIFICATION_BOOKING_CREATED;

    String twentyFourHour =
        formatter.formatForEmail(created, data, berlin, Locale.US, BookingTimeFormat.H24);
    String twelveHour =
        formatter.formatForEmail(created, data, berlin, Locale.UK, BookingTimeFormat.H12);

    assertTrue(twentyFourHour.contains("Oct 8, 2026, 06:00 CEST (Europe/Berlin)"), twentyFourHour);
    assertFalse(twentyFourHour.contains("AM"), twentyFourHour);
    assertTrue(
        normalised(twelveHour).contains("8 Oct 2026, 6:00 am CEST (Europe/Berlin)"), twelveHour);
    for (BookingTimeFormat automatic :
        new BookingTimeFormat[] {BookingTimeFormat.AUTOMATIC, null}) {
      assertEquals(
          formatter.formatForEmail(created, data, berlin, Locale.US),
          formatter.formatForEmail(created, data, berlin, Locale.US, automatic));
    }
    assertTrue(
        normalised(formatter.formatForEmail(created, data, berlin, Locale.US))
            .contains("Oct 8, 2026, 6:00 AM CEST (Europe/Berlin)"));
  }

  @Test
  void inAppMessagesPreferTheExplicitClockOverTheBrowserRegion() {
    BookingNotificationData data = data("2026-10-08T04:00:00Z", "2026-10-08T05:00:00Z");
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    NotificationType created = NotificationType.NOTIFICATION_BOOKING_CREATED;
    Locale british = Locale.forLanguageTag("en-GB");

    String twentyFourHour =
        formatter.format(created, data, berlin, Locale.US, Locale.US, BookingTimeFormat.H24);
    String twelveHour =
        formatter.format(created, data, berlin, Locale.US, british, BookingTimeFormat.H12);

    assertTrue(twentyFourHour.contains("Oct 8, 2026, 06:00 CEST (Europe/Berlin)"), twentyFourHour);
    assertTrue(
        normalised(twelveHour).contains("Oct 8, 2026, 6:00 AM CEST (Europe/Berlin)"), twelveHour);
    assertEquals(
        formatter.format(created, data, berlin, Locale.US, british),
        formatter.format(created, data, berlin, Locale.US, british, BookingTimeFormat.AUTOMATIC));
    assertEquals(
        formatter.format(created, data, berlin, Locale.US),
        formatter.format(created, data, berlin, Locale.US, null, null));
  }

  @Test
  void midnightIsZeroHundredOnTheTwentyFourHourClock() {
    String message =
        formatter.formatForEmail(
            NotificationType.NOTIFICATION_BOOKING_CREATED,
            data("2026-10-07T22:00:00Z", "2026-10-07T23:00:00Z"),
            ZoneId.of("Europe/Berlin"),
            Locale.US,
            BookingTimeFormat.H24);

    assertTrue(message.contains("Oct 8, 2026, 00:00 CEST (Europe/Berlin"), message);
  }

  @Test
  void cancellationReasonIsEscapedAndSelectsTheReasonMessage() {
    BookingNotificationData data = data("2026-01-02T03:04:05Z", "2026-01-02T04:04:05Z");
    data.setCancellationReason("<script>alert('x')</script> & \"repair\"");

    String message =
        formatter.format(
            NotificationType.NOTIFICATION_BOOKING_CANCELLED,
            data,
            ZoneId.of("Europe/Berlin"),
            Locale.US);

    assertTrue(message.contains("Reason: &lt;script&gt;alert('x')&lt;/script&gt;"), message);
    assertTrue(message.contains("&amp; &quot;repair&quot;"), message);
    assertFalse(message.contains("<script>"), message);
  }

  @Test
  void resolvesTheRegionalLocaleFromTheHighestWeightedAcceptLanguage() {
    assertEquals(
        Locale.forLanguageTag("en-GB"),
        BookingNotificationMessageFormatter.regionalLocaleFrom("en-GB,en-US;q=0.9,en;q=0.8"));
    assertEquals(
        Locale.forLanguageTag("fr-FR"),
        BookingNotificationMessageFormatter.regionalLocaleFrom("en-US;q=0.5,fr-FR"));
    assertEquals(
        Locale.forLanguageTag("en-GB"),
        BookingNotificationMessageFormatter.regionalLocaleFrom("fr;q=0,*,en-GB;q=0.8"));
    assertNull(BookingNotificationMessageFormatter.regionalLocaleFrom(null));
    assertNull(BookingNotificationMessageFormatter.regionalLocaleFrom(" "));
    assertNull(BookingNotificationMessageFormatter.regionalLocaleFrom("*"));
    assertNull(BookingNotificationMessageFormatter.regionalLocaleFrom("garbage;;;"));
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
        "08:00",
        "18:00",
        mode,
        customTimezone,
        BookingTimeFormat.AUTOMATIC,
        institutionTimezone,
        true);
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

  private static String normalised(String value) {
    return value.replace('\u202f', ' ');
  }
}
