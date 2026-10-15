package com.researchspace.booking.service;

import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.booking.service.BookingPolicyException.Reason;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingIncrementUnit;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingSchedulingPolicyImplTest {

  private final BookingSchedulingPolicy policy = new BookingSchedulingPolicy();

  @Test
  void usesEachWeekdaysOwnIntervalAndRejectsClosedDays() {
    BookingConfiguration configuration =
        configuration(
            "UTC",
            List.of(
                new BookingOpeningHoursEntry(1, "09:00", "17:00"),
                new BookingOpeningHoursEntry(2, "10:00", "16:00")));
    allowed(configuration, "2026-10-12T09:00:00Z", "2026-10-12T10:00:00Z");
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-13T09:00:00Z", "2026-10-13T10:00:00Z"));
    allowed(configuration, "2026-10-13T10:00:00Z", "2026-10-13T11:00:00Z");
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-14T10:00:00Z", "2026-10-14T11:00:00Z"));
  }

  @Test
  void weekdaysComeFromTheSchedulingZone() {
    BookingConfiguration configuration =
        configuration("Pacific/Kiritimati", hours("09:00", "17:00", 1));
    allowed(configuration, "2026-10-11T19:00:00Z", "2026-10-11T20:00:00Z");
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-12T19:00:00Z", "2026-10-12T20:00:00Z"));
  }

  @Test
  void midnightIsAnExclusiveEndAndConsecutiveIntervalsCanCoverMoreThanOneDate() {
    BookingConfiguration configuration = configuration("UTC", hours("18:00", "24:00", 1));
    allowed(configuration, "2026-10-12T18:00:00Z", "2026-10-13T00:00:00Z");
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-12T18:00:00Z", "2026-10-13T00:05:00Z"));
    configuration.setOpeningHours(
        List.of(
            new BookingOpeningHoursEntry(1, "18:00", "24:00"),
            new BookingOpeningHoursEntry(2, "00:00", "03:00")));
    allowed(configuration, "2026-10-12T18:00:00Z", "2026-10-13T03:00:00Z");
    configuration.setOpeningHours(hours("00:00", "24:00", 1, 3));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-12T10:00:00Z", "2026-10-14T10:00:00Z"));
  }

  @Test
  void retainsGapAndOverlapBoundaryResolutionInTheSchedulingZone() {
    BookingConfiguration configuration = configuration("Europe/Berlin", hours("02:30", "04:00", 7));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-03-29T01:00:00Z", "2026-03-29T01:15:00Z"));
    allowed(configuration, "2026-03-29T01:30:00Z", "2026-03-29T02:00:00Z");
    configuration.setOpeningHours(hours("02:00", "03:00", 7));
    allowed(configuration, "2026-10-25T01:30:00Z", "2026-10-25T01:45:00Z");
  }

  @Test
  void usesTheLargerBufferAsOneSharedGapRatherThanAddingTheValues() {
    BookingConfiguration configuration = configuration("UTC", hours("09:00", "17:00", 3));
    configuration.setBufferBeforeMinutes(10);
    configuration.setBufferAfterMinutes(20);
    Date start = date("2026-10-14T10:00:00Z");
    Date end = date("2026-10-14T11:00:00Z");

    assertEquals(
        new BookingSchedulingPolicy.ConflictInterval(
            date("2026-10-14T09:40:00Z"), date("2026-10-14T11:20:00Z")),
        policy.validate(configuration, start, end));
  }

  @Test
  void maintenanceKeepsItsOpeningAndMaximumExemptionsWhileRetainingGranularityAndBuffers() {
    BookingConfiguration configuration = configuration("UTC", hours("09:00", "17:00", 1));
    configuration.setMaxBookingDurationMinutes(60);
    configuration.setBufferBeforeMinutes(10);
    configuration.setBufferAfterMinutes(20);
    Date start = date("2026-10-14T08:00:00Z");
    Date end = date("2026-10-14T18:00:00Z");
    assertEquals(
        new BookingSchedulingPolicy.ConflictInterval(
            date("2026-10-14T07:40:00Z"), date("2026-10-14T18:20:00Z")),
        policy.validateMaintenance(configuration, start, end));
    assertEquals(
        Reason.MAXIMUM_DURATION,
        rejection(configuration, "2026-10-12T09:00:00Z", "2026-10-12T11:00:00Z"));
    assertEquals(
        Reason.GRANULARITY,
        assertThrows(
                BookingPolicyException.class,
                () -> policy.validateMaintenance(configuration, date("2026-10-14T08:01:01Z"), end))
            .reason());
  }

  private Reason rejection(BookingConfiguration configuration, String start, String end) {
    return assertThrows(
            BookingPolicyException.class,
            () -> policy.validate(configuration, date(start), date(end)))
        .reason();
  }

  private static BookingConfiguration configuration(
      String zone, List<BookingOpeningHoursEntry> openingHours) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone(zone);
    configuration.setSlotGranularityMinutes(5);
    configuration.setOpeningHours(openingHours);
    return configuration;
  }

  private static Date date(String value) {
    return Date.from(Instant.parse(value));
  }

  @Test
  void allDayBookingsSpanOpenDaysButNotAClosedMiddleDay() {
    BookingConfiguration everyDay = configuration("UTC", hours("00:00", "24:00"));
    allowed(everyDay, "2026-08-17T00:00:00Z", "2026-08-31T00:00:00Z");

    BookingConfiguration closedTuesday = configuration("UTC", hours("00:00", "24:00", 1, 3));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(closedTuesday, "2026-08-17T12:00:00Z", "2026-08-19T12:00:00Z"));

    BookingConfiguration partialHours = configuration("UTC", hours("08:00", "18:00", 1, 2));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(partialHours, "2026-08-17T17:00:00Z", "2026-08-18T09:00:00Z"));

    BookingConfiguration allDayMondayPartialTuesday =
        configuration(
            "UTC",
            List.of(
                new BookingOpeningHoursEntry(1, "00:00", "24:00"),
                new BookingOpeningHoursEntry(2, "00:00", "12:00")));
    allowed(allDayMondayPartialTuesday, "2026-08-17T20:00:00Z", "2026-08-18T12:00:00Z");
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(allDayMondayPartialTuesday, "2026-08-17T20:00:00Z", "2026-08-18T12:05:00Z"));
  }

  @Test
  void nonexistentBoundariesShiftForwardByTheGapInBerlin() {
    // Sunday 2026-03-29: 02:00 to 03:00 local does not exist.
    BookingConfiguration configuration = configuration("Europe/Berlin", hours("02:15", "02:45", 7));

    allowed(configuration, "2026-03-29T01:15:00Z", "2026-03-29T01:45:00Z");
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-03-29T01:00:00Z", "2026-03-29T01:15:00Z"));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-03-29T01:45:00Z", "2026-03-29T02:00:00Z"));
  }

  @Test
  void ambiguousBoundariesUseTheEarlierOffsetAndLongDaysCoverAllDay() {
    // Sunday 2026-10-25: 02:00 to 03:00 local occurs twice; 02:30 resolves to 00:30Z.
    BookingConfiguration configuration = configuration("Europe/Berlin", hours("02:30", "24:00", 7));
    allowed(configuration, "2026-10-25T00:30:00Z", "2026-10-25T23:00:00Z");
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-25T00:25:00Z", "2026-10-25T01:00:00Z"));

    BookingConfiguration allDaySunday = configuration("Europe/Berlin", hours("00:00", "24:00", 7));
    allowed(allDaySunday, "2026-10-24T22:00:00Z", "2026-10-25T23:00:00Z");
    allowed(allDaySunday, "2026-03-28T23:00:00Z", "2026-03-29T22:00:00Z");
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(allDaySunday, "2026-10-24T21:55:00Z", "2026-10-25T23:00:00Z"));
  }

  @Test
  void anAlignedIncrementChecksTheLocalStartAndTheElapsedLengthOnly() {
    // Europe/Berlin is UTC+2 in August.
    BookingConfiguration item = configuration("Europe/Berlin", hours("00:00", "24:00"));
    item.setSlotGranularityMinutes(120);
    allowed(item, "2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z");
    assertEquals(Reason.ALIGNMENT, rejection(item, "2026-08-17T07:00:00Z", "2026-08-17T09:00:00Z"));
    assertEquals(
        Reason.GRANULARITY, rejection(item, "2026-08-17T08:00:00Z", "2026-08-17T09:00:00Z"));

    item.setAlignToIncrement(false);
    allowed(item, "2026-08-17T07:00:00Z", "2026-08-17T09:00:00Z");
    assertEquals(
        Reason.GRANULARITY, rejection(item, "2026-08-17T07:00:00Z", "2026-08-17T08:00:00Z"));
  }

  @Test
  void aMissingDayEndNamesTheLocalEndDateAndZone() {
    BookingConfiguration item = configuration("Europe/Berlin", hours("00:00", "24:00"));
    item.setSlotGranularityMinutes(1440);
    item.setIncrementUnit(BookingIncrementUnit.DAYS);

    // 02:30 on Saturday 2026-03-28; 02:30 is skipped on Sunday 2026-03-29.
    BookingPolicyException failure =
        assertThrows(
            BookingPolicyException.class,
            () ->
                policy.validate(item, date("2026-03-28T01:30:00Z"), date("2026-03-29T01:30:00Z")));

    assertEquals(Reason.DAY_END_MISSING, failure.reason());
    assertEquals(
        Optional.of(
            new BookingPolicyException.MissingDayEnd(
                LocalTime.of(2, 30), LocalDate.of(2026, 3, 29), ZoneId.of("Europe/Berlin"))),
        failure.missingDayEnd());
    assertThrows(
        IllegalArgumentException.class, () -> new BookingPolicyException(Reason.DAY_END_MISSING));
  }

  @Test
  void maintenanceIsExemptFromTheTimeIncrement() {
    BookingConfiguration item = configuration("UTC", hours("00:00", "24:00"));
    item.setSlotGranularityMinutes(1440);
    item.setIncrementUnit(BookingIncrementUnit.DAYS);
    assertDoesNotThrow(
        () ->
            policy.validateMaintenance(
                item, date("2026-08-17T09:07:00Z"), date("2026-08-17T11:08:00Z")));
    Date start = date("2026-08-17T09:07:00Z");
    Date subMinuteEnd = date("2026-08-17T11:07:30Z");
    BookingPolicyException failure =
        assertThrows(
            BookingPolicyException.class,
            () -> policy.validateMaintenance(item, start, subMinuteEnd));
    assertEquals(BookingPolicyException.Reason.GRANULARITY, failure.reason());
  }

  private void allowed(BookingConfiguration configuration, String start, String end) {
    assertDoesNotThrow(() -> policy.validate(configuration, date(start), date(end)));
  }
}
