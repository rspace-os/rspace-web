package com.researchspace.booking.service;

import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.booking.service.BookingPolicyException.Reason;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingSchedulingPolicyImplTest {

  private final BookingSchedulingPolicy policy = new BookingSchedulingPolicyImpl();

  @Test
  void usesEachWeekdaysOwnIntervalAndRejectsClosedDays() {
    BookingConfiguration configuration =
        configuration(
            "UTC",
            List.of(
                new BookingOpeningHoursEntry(1, "09:00", "17:00"),
                new BookingOpeningHoursEntry(2, "10:00", "16:00")));
    assertDoesNotThrow(
        () -> validate(configuration, "2026-10-12T09:00:00Z", "2026-10-12T10:00:00Z"));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-13T09:00:00Z", "2026-10-13T10:00:00Z"));
    assertDoesNotThrow(
        () -> validate(configuration, "2026-10-13T10:00:00Z", "2026-10-13T11:00:00Z"));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-14T10:00:00Z", "2026-10-14T11:00:00Z"));
  }

  @Test
  void weekdaysComeFromTheSchedulingZone() {
    BookingConfiguration configuration =
        configuration("Pacific/Kiritimati", hours("09:00", "17:00", 1));
    assertDoesNotThrow(
        () -> validate(configuration, "2026-10-11T19:00:00Z", "2026-10-11T20:00:00Z"));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-12T19:00:00Z", "2026-10-12T20:00:00Z"));
  }

  @Test
  void midnightIsAnExclusiveEndAndConsecutiveIntervalsCanCoverMoreThanOneDate() {
    BookingConfiguration configuration = configuration("UTC", hours("18:00", "24:00", 1));
    assertDoesNotThrow(
        () -> validate(configuration, "2026-10-12T18:00:00Z", "2026-10-13T00:00:00Z"));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(configuration, "2026-10-12T18:00:00Z", "2026-10-13T00:05:00Z"));
    configuration.setOpeningHours(
        List.of(
            new BookingOpeningHoursEntry(1, "18:00", "24:00"),
            new BookingOpeningHoursEntry(2, "00:00", "03:00")));
    assertDoesNotThrow(
        () -> validate(configuration, "2026-10-12T18:00:00Z", "2026-10-13T03:00:00Z"));
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
    assertDoesNotThrow(
        () -> validate(configuration, "2026-03-29T01:30:00Z", "2026-03-29T02:00:00Z"));
    configuration.setOpeningHours(hours("02:00", "03:00", 7));
    assertDoesNotThrow(
        () -> validate(configuration, "2026-10-25T01:30:00Z", "2026-10-25T01:45:00Z"));
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
            date("2026-10-14T07:40:00Z"), date("2026-10-14T18:10:00Z")),
        policy.validateMaintenance(configuration, start, end));
    assertEquals(
        Reason.MAXIMUM_DURATION,
        rejection(configuration, "2026-10-12T09:00:00Z", "2026-10-12T11:00:00Z"));
    assertEquals(
        Reason.GRANULARITY,
        assertThrows(
                BookingPolicyException.class,
                () -> policy.validateMaintenance(configuration, date("2026-10-14T08:01:00Z"), end))
            .reason());
  }

  private Reason rejection(BookingConfiguration configuration, String start, String end) {
    return assertThrows(BookingPolicyException.class, () -> validate(configuration, start, end))
        .reason();
  }

  private void validate(BookingConfiguration configuration, String start, String end) {
    policy.validate(configuration, date(start), date(end));
  }

  private static BookingConfiguration configuration(
      String zone, List<BookingOpeningHoursEntry> openingHours) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone(zone);
    configuration.setOpeningHours(openingHours);
    return configuration;
  }

  private static Date date(String value) {
    return Date.from(Instant.parse(value));
  }
}
