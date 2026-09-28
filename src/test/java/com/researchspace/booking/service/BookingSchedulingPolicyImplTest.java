package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingOpeningException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Opening coverage by weekday; 2026-08-17 is a Monday. */
class BookingSchedulingPolicyImplTest {

  private final BookingSchedulingPolicy policy = new BookingSchedulingPolicyImpl();

  private static BookingConfiguration configuration(
      String zone,
      String start,
      String end,
      List<Integer> openDays,
      List<BookingOpeningException> exceptions) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone(zone);
    configuration.setOpeningStart(start);
    configuration.setOpeningEnd(end);
    configuration.setOpenDays(openDays);
    configuration.setOpeningExceptions(exceptions);
    return configuration;
  }

  private void allowed(BookingConfiguration configuration, String start, String end) {
    assertDoesNotThrow(() -> policy.validate(configuration, date(start), date(end)));
  }

  private void rejected(BookingConfiguration configuration, String start, String end) {
    BookingPolicyException failure =
        assertThrows(
            BookingPolicyException.class,
            () -> policy.validate(configuration, date(start), date(end)));
    assertEquals(BookingPolicyException.Reason.OPENING_HOURS, failure.reason());
  }

  private static Date date(String instant) {
    return Date.from(Instant.parse(instant));
  }

  @Test
  void anExceptionReplacesTheSharedHoursOnlyOnItsDay() {
    BookingConfiguration configuration =
        configuration(
            "UTC",
            "09:00",
            "17:00",
            List.of(1, 2, 3, 4, 5),
            List.of(new BookingOpeningException(2, "10:00", "16:00")));

    allowed(configuration, "2026-08-17T09:30:00Z", "2026-08-17T10:30:00Z");
    rejected(configuration, "2026-08-18T09:30:00Z", "2026-08-18T10:30:00Z");
    allowed(configuration, "2026-08-18T10:00:00Z", "2026-08-18T16:00:00Z");
    rejected(configuration, "2026-08-18T15:30:00Z", "2026-08-18T16:30:00Z");
    rejected(configuration, "2026-08-17T16:30:00Z", "2026-08-17T17:30:00Z");
    rejected(configuration, "2026-08-22T10:00:00Z", "2026-08-22T11:00:00Z");

    configuration.setOpeningStart("07:00");
    allowed(configuration, "2026-08-19T07:00:00Z", "2026-08-19T08:00:00Z");
    rejected(configuration, "2026-08-18T09:30:00Z", "2026-08-18T10:30:00Z");
  }

  @Test
  void aMondayOnlyAllDayItemAllowsAnEndAtTuesdayMidnightButNotLater() {
    BookingConfiguration configuration =
        configuration("UTC", "00:00", "24:00", List.of(1), List.of());

    allowed(configuration, "2026-08-17T00:00:00Z", "2026-08-18T00:00:00Z");
    rejected(configuration, "2026-08-17T00:00:00Z", "2026-08-18T00:05:00Z");
    rejected(configuration, "2026-08-16T23:55:00Z", "2026-08-17T01:00:00Z");
  }

  @Test
  void allDayBookingsSpanOpenDaysButNotAClosedMiddleDay() {
    BookingConfiguration everyDay =
        configuration("UTC", "00:00", "24:00", List.of(1, 2, 3, 4, 5, 6, 7), List.of());
    allowed(everyDay, "2026-08-17T00:00:00Z", "2026-08-31T00:00:00Z");

    BookingConfiguration closedTuesday =
        configuration("UTC", "00:00", "24:00", List.of(1, 3), List.of());
    rejected(closedTuesday, "2026-08-17T12:00:00Z", "2026-08-19T12:00:00Z");

    BookingConfiguration partialHours =
        configuration("UTC", "08:00", "18:00", List.of(1, 2), List.of());
    rejected(partialHours, "2026-08-17T17:00:00Z", "2026-08-18T09:00:00Z");

    BookingConfiguration allDayMondayPartialTuesday =
        configuration(
            "UTC",
            "00:00",
            "24:00",
            List.of(1, 2),
            List.of(new BookingOpeningException(2, "00:00", "12:00")));
    allowed(allDayMondayPartialTuesday, "2026-08-17T20:00:00Z", "2026-08-18T12:00:00Z");
    rejected(allDayMondayPartialTuesday, "2026-08-17T20:00:00Z", "2026-08-18T12:05:00Z");
  }

  @Test
  void theWeekdayIsTheSchedulingZoneWeekday() {
    BookingConfiguration mondayInAuckland =
        configuration("Pacific/Auckland", "00:00", "24:00", List.of(1), List.of());
    // Sunday 2026-08-16 20:00 UTC is Monday 08:00 in Auckland.
    allowed(mondayInAuckland, "2026-08-16T20:00:00Z", "2026-08-16T21:00:00Z");
    rejected(mondayInAuckland, "2026-08-17T20:00:00Z", "2026-08-17T21:00:00Z");

    BookingConfiguration mondayInHonolulu =
        configuration("Pacific/Honolulu", "00:00", "24:00", List.of(1), List.of());
    // Tuesday 2026-08-18 05:00 UTC is Monday 19:00 in Honolulu.
    allowed(mondayInHonolulu, "2026-08-18T05:00:00Z", "2026-08-18T06:00:00Z");
    rejected(mondayInHonolulu, "2026-08-17T05:00:00Z", "2026-08-17T06:00:00Z");
  }

  @Test
  void nonexistentBoundariesShiftForwardByTheGapInBerlin() {
    // Sunday 2026-03-29: 02:00 to 03:00 local does not exist.
    BookingConfiguration configuration =
        configuration("Europe/Berlin", "02:15", "02:45", List.of(7), List.of());

    allowed(configuration, "2026-03-29T01:15:00Z", "2026-03-29T01:45:00Z");
    rejected(configuration, "2026-03-29T01:00:00Z", "2026-03-29T01:15:00Z");
    rejected(configuration, "2026-03-29T01:45:00Z", "2026-03-29T02:00:00Z");
  }

  @Test
  void ambiguousBoundariesUseTheEarlierOffsetAndLongDaysCoverAllDay() {
    // Sunday 2026-10-25: 02:00 to 03:00 local occurs twice; 02:30 resolves to 00:30Z.
    BookingConfiguration configuration =
        configuration("Europe/Berlin", "02:30", "24:00", List.of(7), List.of());
    allowed(configuration, "2026-10-25T00:30:00Z", "2026-10-25T23:00:00Z");
    rejected(configuration, "2026-10-25T00:25:00Z", "2026-10-25T01:00:00Z");

    BookingConfiguration allDaySunday =
        configuration("Europe/Berlin", "00:00", "24:00", List.of(7), List.of());
    allowed(allDaySunday, "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z");
    allowed(allDaySunday, "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z");
    rejected(allDaySunday, "2026-10-24T21:55:00Z", "2026-10-25T23:00:00Z");
  }

  @Test
  void maintenanceIsExemptFromOpeningHoursIncludingClosedDays() {
    BookingConfiguration configuration =
        configuration("UTC", "09:00", "17:00", List.of(1), List.of());

    assertDoesNotThrow(
        () ->
            policy.validateMaintenance(
                configuration, date("2026-08-18T20:00:00Z"), date("2026-08-19T02:00:00Z")));
    rejected(configuration, "2026-08-18T10:00:00Z", "2026-08-18T11:00:00Z");
  }
}
