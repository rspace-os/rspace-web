package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.researchspace.booking.service.BookingPolicyException.Reason;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingIncrementUnit;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BookingSchedulingPolicyFuzzTest {

  private static final int[] GRANULARITIES = {1, 5, 10, 15};
  private static final String[] ZONES = {
    "UTC", "Europe/Berlin", "Australia/Lord_Howe", "Asia/Kathmandu"
  };
  private static final LocalDate[] DATES = {
    LocalDate.of(2026, 2, 7),
    LocalDate.of(2026, 3, 29),
    LocalDate.of(2026, 10, 25),
    LocalDate.of(2026, 4, 5),
    LocalDate.of(2026, 10, 4)
  };
  private static final List<Anchor> ANCHORS =
      List.of(
          new Anchor("UTC", "2026-02-07T00:00:00Z"),
          new Anchor("Europe/Berlin", "2026-03-29T00:00:00Z"),
          new Anchor("Europe/Berlin", "2026-10-25T00:00:00Z"),
          new Anchor("Europe/Berlin", "2026-10-25T01:00:00Z"),
          new Anchor("Australia/Lord_Howe", "2026-04-04T14:30:00Z"),
          new Anchor("Australia/Lord_Howe", "2026-04-04T15:00:00Z"),
          new Anchor("Australia/Lord_Howe", "2026-10-03T15:00:00Z"),
          new Anchor("Asia/Kathmandu", "2026-02-06T18:15:00Z"));

  @FuzzTest(maxDuration = "30s")
  void validBookingsAndConflicts(FuzzedDataProvider data) {
    Anchor anchor = ANCHORS.get(data.consumeInt(0, ANCHORS.size() - 1));
    int granularity = GRANULARITIES[data.consumeInt(0, GRANULARITIES.length - 1)];
    Instant start =
        Instant.parse(anchor.instant())
            .plus((long) granularity * data.consumeInt(0, 96), ChronoUnit.MINUTES);
    // At most two elapsed days, well below the caller's absolute booking-length limit.
    long duration = (long) granularity * data.consumeInt(1, 2880 / granularity);
    Instant end = start.plus(duration, ChronoUnit.MINUTES);
    BookingConfiguration configuration = configuration(data, anchor.zone(), granularity);
    configuration.setMaxBookingDurationMinutes(data.consumeBoolean() ? duration : 0);

    BookingSchedulingPolicy policy = new BookingSchedulingPolicy();
    assertConflict(
        configuration, start, end, policy.validate(configuration, date(start), date(end)));
    assertConflict(
        configuration,
        start,
        end,
        policy.validateMaintenance(configuration, date(start), date(end)));
  }

  @FuzzTest(maxDuration = "30s")
  void enforcesSingleRuleBoundaries(FuzzedDataProvider data) {
    String zone = ZONES[data.consumeInt(0, ZONES.length - 1)];
    int granularity = GRANULARITIES[data.consumeInt(0, GRANULARITIES.length - 1)];
    LocalDate day = DATES[data.consumeInt(0, DATES.length - 1)];
    BookingConfiguration configuration = configuration(data, zone, granularity);
    BookingSchedulingPolicy policy = new BookingSchedulingPolicy();
    Instant start = day.atTime(9, 0).atZone(ZoneId.of(zone)).toInstant();
    long maximum = (long) granularity * data.consumeInt(1, 480 / granularity);
    Instant end = start.plus(maximum, ChronoUnit.MINUTES);

    assertConflict(
        configuration, start, end, policy.validate(configuration, date(start), date(end)));
    long shiftMillis =
        switch (data.consumeInt(0, granularity == 1 ? 1 : 2)) {
          case 0 -> 1;
          case 1 -> 1000;
          default -> 60_000;
        };
    Instant shiftedStart = start.plusMillis(shiftMillis);
    Instant shiftedEnd = end.plusMillis(shiftMillis);
    if (shiftMillis < 60_000)
      assertRejection(Reason.GRANULARITY, configuration, shiftedStart, shiftedEnd, false);
    assertRejection(Reason.GRANULARITY, configuration, start, shiftedEnd, false);
    if (shiftMillis < 60_000) {
      assertRejection(Reason.GRANULARITY, configuration, shiftedStart, end, true);
      assertRejection(Reason.GRANULARITY, configuration, start, shiftedEnd, true);
    } else {
      assertConflict(
          configuration,
          shiftedStart,
          end,
          policy.validateMaintenance(configuration, date(shiftedStart), date(end)));
      assertConflict(
          configuration,
          start,
          shiftedEnd,
          policy.validateMaintenance(configuration, date(start), date(shiftedEnd)));
    }

    configuration.setMaxBookingDurationMinutes(maximum);
    assertConflict(
        configuration, start, end, policy.validate(configuration, date(start), date(end)));
    Instant beyondMaximum = end.plus(granularity, ChronoUnit.MINUTES);
    assertRejection(Reason.MAXIMUM_DURATION, configuration, start, beyondMaximum, false);
    assertConflict(
        configuration,
        start,
        beyondMaximum,
        policy.validateMaintenance(configuration, date(start), date(beyondMaximum)));

    configuration.setMaxBookingDurationMinutes(0);
    configuration.setOpeningHours(
        List.of(new BookingOpeningHoursEntry(day.getDayOfWeek().getValue(), "09:00", "17:00")));
    Instant closing = day.atTime(17, 0).atZone(ZoneId.of(zone)).toInstant();
    assertConflict(
        configuration, start, closing, policy.validate(configuration, date(start), date(closing)));
    Instant beforeOpening = start.minus(granularity, ChronoUnit.MINUTES);
    Instant afterClosing = closing.plus(granularity, ChronoUnit.MINUTES);
    assertRejection(Reason.OPENING_HOURS, configuration, beforeOpening, closing, false);
    assertRejection(Reason.OPENING_HOURS, configuration, start, afterClosing, false);
    assertConflict(
        configuration,
        beforeOpening,
        closing,
        policy.validateMaintenance(configuration, date(beforeOpening), date(closing)));
    assertConflict(
        configuration,
        start,
        afterClosing,
        policy.validateMaintenance(configuration, date(start), date(afterClosing)));
  }

  @FuzzTest(maxDuration = "30s")
  void elapsedIncrements(FuzzedDataProvider data) {
    Anchor anchor = ANCHORS.get(data.consumeInt(0, ANCHORS.size() - 1));
    int[] increments = {1, 5, 10, 15, 17, 60, 90, 120, 1440, 2880};
    int increment = increments[data.consumeInt(0, increments.length - 1)];
    BookingConfiguration configuration = configuration(data, anchor.zone(), increment);
    configuration.setIncrementUnit(
        data.consumeBoolean() ? BookingIncrementUnit.HOURS : BookingIncrementUnit.MINUTES);
    Instant start =
        Instant.parse(anchor.instant()).plus(data.consumeInt(0, 1439), ChronoUnit.MINUTES);
    Instant end = start.plus((long) increment * data.consumeInt(1, 2), ChronoUnit.MINUTES);
    long elapsedMinutes = Duration.between(start, end).toMinutes();
    configuration.setMaxBookingDurationMinutes(elapsedMinutes);
    BookingSchedulingPolicy policy = new BookingSchedulingPolicy();

    assertConflict(
        configuration, start, end, policy.validate(configuration, date(start), date(end)));
    if (increment > 1) {
      assertRejection(
          Reason.GRANULARITY, configuration, start, end.plus(1, ChronoUnit.MINUTES), false);
    }
    // Maintenance ignores duration increments and the finite item maximum.
    Instant maintenanceEnd = end.plus(1, ChronoUnit.MINUTES);
    assertConflict(
        configuration,
        start,
        maintenanceEnd,
        policy.validateMaintenance(configuration, date(start), date(maintenanceEnd)));
  }

  @FuzzTest(maxDuration = "30s")
  void calendarDayIncrements(FuzzedDataProvider data) {
    String zone = ZONES[data.consumeInt(0, ZONES.length - 1)];
    LocalDate day = DATES[data.consumeInt(0, DATES.length - 1)].plusDays(data.consumeInt(-2, 1));
    int incrementDays = data.consumeInt(1, 3);
    int days = incrementDays * data.consumeInt(1, 3);
    // Midnight and 04:00 onward exist on all the fixed transition dates in these zones.
    int hour = data.consumeInt(0, 20);
    LocalDateTime localStart = day.atTime(hour == 0 ? 0 : hour + 3, data.consumeInt(0, 59));
    LocalDateTime localEnd = localStart.plusDays(days);
    BookingConfiguration configuration = configuration(data, zone, incrementDays * 1440);
    configuration.setIncrementUnit(BookingIncrementUnit.DAYS);
    Instant start = localStart.atZone(ZoneId.of(zone)).toInstant();
    Instant end = localEnd.atZone(ZoneId.of(zone)).toInstant();
    assertEquals(localStart.toLocalTime(), end.atZone(ZoneId.of(zone)).toLocalTime());
    assertEquals(days, ChronoUnit.DAYS.between(day, end.atZone(ZoneId.of(zone)).toLocalDate()));
    long elapsed = Duration.between(start, end).toMinutes();
    long nominal = incrementDays * 1440L;
    for (long maximum : new long[] {0, nominal, elapsed - 1, elapsed, elapsed + 1}) {
      if (maximum == 0 || maximum >= nominal) {
        configuration.setMaxBookingDurationMinutes(maximum);
        assertElapsedMaximum(configuration, start, end);
      }
    }
    configuration.setMaxBookingDurationMinutes(0);
    assertRejection(
        Reason.GRANULARITY, configuration, start, end.plus(1, ChronoUnit.MINUTES), false);
    if (incrementDays > 1) {
      Instant wrongDate = localEnd.plusDays(1).atZone(ZoneId.of(zone)).toInstant();
      assertRejection(Reason.GRANULARITY, configuration, start, wrongDate, false);
    }

    configuration.setMaxBookingDurationMinutes(data.consumeLong(1, nominal - 1));
    assertInvalidMaximum(configuration);

    boolean berlin = data.consumeBoolean();
    String gapZone = berlin ? "Europe/Berlin" : "Australia/Lord_Howe";
    LocalDate gapDate = berlin ? LocalDate.of(2026, 3, 29) : LocalDate.of(2026, 10, 4);
    LocalTime missingTime = LocalTime.of(2, data.consumeInt(0, berlin ? 59 : 29));
    LocalDateTime missingEnd = gapDate.atTime(missingTime);
    assertEquals(List.of(), ZoneId.of(gapZone).getRules().getValidOffsets(missingEnd));
    BookingConfiguration missing = configuration(data, gapZone, incrementDays * 1440);
    missing.setIncrementUnit(BookingIncrementUnit.DAYS);
    Instant gapStart = missingEnd.minusDays(days).atZone(ZoneId.of(gapZone)).toInstant();
    Instant shiftedEnd = missingEnd.atZone(ZoneId.of(gapZone)).toInstant();
    BookingPolicyException failure =
        assertRejection(Reason.DAY_END_MISSING, missing, gapStart, shiftedEnd, false);
    assertEquals(
        new BookingPolicyException.MissingDayEnd(missingTime, gapDate, ZoneId.of(gapZone)),
        failure.missingDayEnd().orElseThrow());
    assertConflict(
        missing,
        gapStart,
        shiftedEnd,
        new BookingSchedulingPolicy()
            .validateMaintenance(missing, date(gapStart), date(shiftedEnd)));
  }

  @Test
  void springDayMaximumBelowNominalIsInvalidSettings() {
    BookingConfiguration configuration = configuration("Europe/Berlin", 1440);
    configuration.setIncrementUnit(BookingIncrementUnit.DAYS);
    configuration.setMaxBookingDurationMinutes(1379);
    assertInvalidMaximum(configuration);
  }

  private static void assertInvalidMaximum(BookingConfiguration configuration) {
    BookingSchedulingSettings settings = BookingSchedulingSettings.from(configuration);
    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> BookingSettingsValidation.requireValid(settings));
    assertEquals(
        InvalidBookingSchedulingSettingsException.Reason.MAXIMUM_DURATION, failure.reason());
  }

  private static void assertElapsedMaximum(
      BookingConfiguration configuration, Instant start, Instant end) {
    BookingSettingsValidation.requireValid(BookingSchedulingSettings.from(configuration));
    long elapsed = Duration.between(start, end).toMinutes();
    long maximum = configuration.getMaxBookingDurationMinutes();
    if (maximum > 0 && elapsed > maximum) {
      BookingPolicyException failure =
          assertRejection(Reason.MAXIMUM_DURATION, configuration, start, end, false);
      assertEquals(maximum, failure.maximumDurationMinutes().orElseThrow());
    } else {
      assertConflict(
          configuration,
          start,
          end,
          new BookingSchedulingPolicy().validate(configuration, date(start), date(end)));
    }
  }

  private static BookingConfiguration configuration(
      FuzzedDataProvider data, String zone, int granularity) {
    BookingConfiguration configuration = configuration(zone, granularity);
    configuration.setBufferBeforeMinutes(data.consumeInt(0, 10_080));
    configuration.setBufferAfterMinutes(data.consumeInt(0, 10_080));
    return configuration;
  }

  private static BookingConfiguration configuration(String zone, int granularity) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone(zone);
    configuration.setSlotGranularityMinutes(granularity);
    configuration.setOpeningHours(
        IntStream.rangeClosed(1, 7)
            .mapToObj(day -> new BookingOpeningHoursEntry(day, "00:00", "24:00"))
            .toList());
    return configuration;
  }

  private static void assertConflict(
      BookingConfiguration configuration,
      Instant start,
      Instant end,
      BookingSchedulingPolicy.ConflictInterval actual) {
    long gap =
        Math.max(configuration.getBufferBeforeMinutes(), configuration.getBufferAfterMinutes());
    assertEquals(date(start.minus(gap, ChronoUnit.MINUTES)), actual.start());
    assertEquals(date(end.plus(gap, ChronoUnit.MINUTES)), actual.end());
  }

  private static BookingPolicyException assertRejection(
      Reason expected,
      BookingConfiguration configuration,
      Instant start,
      Instant end,
      boolean maintenance) {
    BookingSchedulingPolicy policy = new BookingSchedulingPolicy();
    Date startDate = date(start);
    Date endDate = date(end);
    BookingPolicyException failure =
        assertThrows(
            BookingPolicyException.class,
            () -> {
              if (maintenance) {
                policy.validateMaintenance(configuration, startDate, endDate);
              } else {
                policy.validate(configuration, startDate, endDate);
              }
            });
    assertEquals(expected, failure.reason());
    return failure;
  }

  private static Date date(Instant instant) {
    return Date.from(instant);
  }

  private record Anchor(String zone, String instant) {}
}
