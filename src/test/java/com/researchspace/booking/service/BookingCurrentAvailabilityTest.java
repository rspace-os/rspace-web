package com.researchspace.booking.service;

import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.booking.dao.TimeSlotBookingDao.EventInterval;
import com.researchspace.booking.service.BookingCatalogueManager.Availability;
import com.researchspace.booking.service.BookingCatalogueManager.AvailabilityWindow;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The All bookable items quick-filter classification. The cases mirror the client tests ({@code
 * availabilityQuickFilters.test.ts}); 2026-08-17 is a Monday.
 */
class BookingCurrentAvailabilityTest {

  private static final Optional<Availability> NOW = Optional.of(Availability.AVAILABLE_NOW);
  private static final Optional<Availability> LATER = Optional.of(Availability.FREE_LATER_TODAY);
  private static final Optional<Availability> NEITHER = Optional.empty();

  private static final AvailabilityWindow UTC_DAY_AT_NINE =
      window("2026-08-17T00:00:00Z", "2026-08-18T00:00:00Z", "2026-08-17T09:00:00Z");

  @Test
  void classifiesEachItemInItsOwnSchedulingTimeZone() {
    List<EventInterval> busyUntilTen =
        List.of(event(BookingEventKind.BOOKING, "2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z"));

    assertEquals(LATER, classify(configuration("UTC"), busyUntilTen, UTC_DAY_AT_NINE));
    assertEquals(NOW, classify(configuration("America/Los_Angeles"), List.of(), UTC_DAY_AT_NINE));
  }

  @Test
  void distinguishesBeforeOpeningOpenNowAndAfterClosing() {
    BookingConfiguration restricted = configuration("UTC");
    restricted.setOpeningHours(hours("08:00", "18:00"));

    assertEquals(LATER, classify(restricted, List.of(), atUtc("2026-08-17T07:00:00Z")));
    assertEquals(NOW, classify(restricted, List.of(), atUtc("2026-08-17T09:00:00Z")));
    assertEquals(NEITHER, classify(restricted, List.of(), atUtc("2026-08-17T19:00:00Z")));
  }

  @Test
  void treatsAClosedWeekdayAsUnavailableAndUsesIndependentHours() {
    BookingConfiguration closedMonday = configuration("UTC");
    closedMonday.setOpeningHours(hours("00:00", "24:00", 2, 3, 4, 5, 6, 7));
    BookingConfiguration lateMonday = configuration("UTC");
    lateMonday.setOpeningHours(hours("12:00", "18:00", 1));

    assertEquals(NEITHER, classify(closedMonday, List.of(), UTC_DAY_AT_NINE));
    assertEquals(LATER, classify(lateMonday, List.of(), UTC_DAY_AT_NINE));
  }

  @Test
  void treatsAWindowCollapsedByDaylightSavingAsUnavailable() {
    // Berlin 02:30-03:00 on 2026-03-29 falls in the skipped hour, so both ends are 01:00Z.
    AvailabilityWindow collapsed =
        window("2026-03-29T01:00:00Z", "2026-03-29T01:00:00Z", "2026-03-29T00:00:00Z");

    assertEquals(NEITHER, classify(configuration("Europe/Berlin"), List.of(), collapsed));
  }

  @Test
  void countsMaintenanceEvenWhenDoubleBookingIsAllowed() {
    BookingConfiguration shared = configuration("UTC");
    shared.setAllowDoubleBooking(true);
    List<EventInterval> bookingUntilTen =
        List.of(event(BookingEventKind.BOOKING, "2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z"));
    List<EventInterval> maintenanceUntilTen =
        List.of(
            event(BookingEventKind.MAINTENANCE, "2026-08-17T08:00:00Z", "2026-08-17T10:00:00Z"));

    assertEquals(NOW, classify(shared, bookingUntilTen, UTC_DAY_AT_NINE));
    assertEquals(LATER, classify(shared, maintenanceUntilTen, UTC_DAY_AT_NINE));
  }

  @ParameterizedTest
  @CsvSource({"10, 20", "20, 10"})
  void widensEventsByTheSharedBufferGap(int before, int after) {
    BookingConfiguration buffered = configuration("UTC");
    buffered.setBufferBeforeMinutes(before);
    buffered.setBufferAfterMinutes(after);
    List<EventInterval> startsAtQuarterPastNine =
        List.of(event(BookingEventKind.BOOKING, "2026-08-17T09:15:00Z", "2026-08-17T10:00:00Z"));
    List<EventInterval> endedAtQuarterToNine =
        List.of(event(BookingEventKind.BOOKING, "2026-08-17T08:00:00Z", "2026-08-17T08:45:00Z"));
    // The buffer ends at 09:00 exactly, and intervals are half-open.
    List<EventInterval> endedAtTwentyToNine =
        List.of(event(BookingEventKind.BOOKING, "2026-08-17T08:00:00Z", "2026-08-17T08:40:00Z"));

    assertEquals(LATER, classify(buffered, startsAtQuarterPastNine, UTC_DAY_AT_NINE));
    assertEquals(LATER, classify(buffered, endedAtQuarterToNine, UTC_DAY_AT_NINE));
    assertEquals(NOW, classify(buffered, endedAtTwentyToNine, UTC_DAY_AT_NINE));
  }

  @Test
  void isNeitherWhenBusyForTheRestOfTheWindow() {
    List<EventInterval> busyAllDay =
        List.of(event(BookingEventKind.BOOKING, "2026-08-17T00:00:00Z", "2026-08-18T00:00:00Z"));
    AvailabilityWindow ended =
        window("2026-08-17T08:00:00Z", "2026-08-17T18:00:00Z", "2026-08-17T18:00:00Z");

    assertEquals(NEITHER, classify(configuration("UTC"), busyAllDay, UTC_DAY_AT_NINE));
    assertEquals(NEITHER, classify(configuration("UTC"), List.of(), ended));
  }

  @Test
  void anItemFreeOnlyBeforeNowIsNotFreeLater() {
    List<EventInterval> busyFromNine =
        List.of(event(BookingEventKind.BOOKING, "2026-08-17T09:00:00Z", "2026-08-18T00:00:00Z"));

    assertEquals(NEITHER, classify(configuration("UTC"), busyFromNine, UTC_DAY_AT_NINE));
  }

  @Test
  void uses24HourClosingAndTheSchedulingZoneAcrossTheDisplayDay() {
    // Tokyo is UTC+9: a 00:00-24:00 display day in UTC spans two Tokyo dates.
    BookingConfiguration evenings = configuration("Asia/Tokyo");
    evenings.setOpeningHours(hours("18:00", "24:00"));

    // 09:00Z is 18:00 in Tokyo, the opening instant.
    assertEquals(NOW, classify(evenings, List.of(), UTC_DAY_AT_NINE));
    assertEquals(LATER, classify(evenings, List.of(), atUtc("2026-08-17T08:59:00Z")));
    assertEquals(NEITHER, classify(evenings, List.of(), atUtc("2026-08-17T15:00:00Z")));
  }

  private static Optional<Availability> classify(
      BookingConfiguration configuration, List<EventInterval> events, AvailabilityWindow window) {
    return BookingCurrentAvailability.classify(configuration, events, window);
  }

  private static BookingConfiguration configuration(String zone) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(1L);
    configuration.setTimeZone(zone);
    configuration.setOpeningHours(hours("00:00", "24:00"));
    configuration.setBufferBeforeMinutes(0);
    configuration.setBufferAfterMinutes(0);
    configuration.setAllowDoubleBooking(false);
    return configuration;
  }

  private static AvailabilityWindow atUtc(String now) {
    return window("2026-08-17T00:00:00Z", "2026-08-18T00:00:00Z", now);
  }

  private static AvailabilityWindow window(String start, String end, String now) {
    return new AvailabilityWindow(Instant.parse(start), Instant.parse(end), Instant.parse(now));
  }

  private static EventInterval event(BookingEventKind kind, String start, String end) {
    return new EventInterval(
        1L, kind, Date.from(Instant.parse(start)), Date.from(Instant.parse(end)));
  }
}
