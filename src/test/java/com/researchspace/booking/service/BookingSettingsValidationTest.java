package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingIncrementUnit;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingSettingsValidationTest {

  private final BookingSchedulingSettings current =
      new BookingSchedulingSettings(
          5,
          BookingIncrementUnit.MINUTES,
          List.of(
              new BookingOpeningHoursEntry(1, "09:00", "17:00"),
              new BookingOpeningHoursEntry(6, "10:00", "16:00")),
          0,
          0,
          0,
          false);

  @Test
  void acceptsIndependentAndPartialDayIntervalsEndingAtMidnight() {
    BookingSchedulingSettings settings =
        patch(
                List.of(
                    new BookingOpeningHoursEntry(1, "09:00", "17:00"),
                    new BookingOpeningHoursEntry(6, "18:00", "24:00")))
            .merge(current);
    assertDoesNotThrow(() -> BookingSettingsValidation.requireValid(settings));
  }

  @Test
  void rejectsEmptyDuplicateOutOfRangeAndIncompleteDays() {
    assertEquals(Reason.OPENING_HOURS, rejection(patch(List.of()).merge(current)));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(
            patch(
                    List.of(
                        new BookingOpeningHoursEntry(1, "09:00", "12:00"),
                        new BookingOpeningHoursEntry(1, "13:00", "17:00")))
                .merge(current)));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(
            patch(List.of(new BookingOpeningHoursEntry(0, "09:00", "17:00"))).merge(current)));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(
            patch(Arrays.asList(new BookingOpeningHoursEntry(1, "09:00", "17:00"), null))
                .merge(current)));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(patch(List.of(new BookingOpeningHoursEntry(1, null, "17:00"))).merge(current)));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(
            patch(List.of(new BookingOpeningHoursEntry(1, "18:00", "08:00"))).merge(current)));
  }

  private Reason rejection(BookingSchedulingSettings settings) {
    return assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> BookingSettingsValidation.requireValid(settings))
        .reason();
  }

  private static BookingSchedulingSettings.Patch patch(List<BookingOpeningHoursEntry> hours) {
    return new BookingSchedulingSettings.Patch(null, hours, null, null, null, null);
  }

  @Test
  void durationIncrementMustFitContinuousOpeningIncludingWeekBoundary() {
    BookingSchedulingSettings twoDays =
        new BookingSchedulingSettings(
            2880,
            BookingIncrementUnit.MINUTES,
            List.of(
                new BookingOpeningHoursEntry(7, "00:00", "24:00"),
                new BookingOpeningHoursEntry(1, "00:00", "24:00")),
            0,
            0,
            0,
            false);
    assertDoesNotThrow(() -> BookingSettingsValidation.requireValid(twoDays));
    BookingSchedulingSettings disconnected =
        patch(
                List.of(
                    new BookingOpeningHoursEntry(1, "00:00", "24:00"),
                    new BookingOpeningHoursEntry(3, "00:00", "24:00")))
            .merge(twoDays);
    assertEquals(Reason.GRANULARITY, rejection(disconnected));
    BookingSchedulingSettings alwaysOpen =
        new BookingSchedulingSettings(
            527040,
            BookingIncrementUnit.MINUTES,
            BookingSchedulingSettings.DEFAULT_OPENING_HOURS,
            0,
            0,
            0,
            false);
    assertDoesNotThrow(() -> BookingSettingsValidation.requireValid(alwaysOpen));
    assertEquals(480, current.longestOpeningMinutes());
  }

  @Test
  void validatesTheReplacementScheduleAgainstTheMergedIncrement() {
    assertDoesNotThrow(() -> BookingSettingsValidation.requireValid(current));
    assertDoesNotThrow(
        () ->
            BookingSettingsValidation.requireValid(
                patch(List.of(new BookingOpeningHoursEntry(2, "18:00", "24:00"))).merge(current)));
    assertEquals(
        Reason.GRANULARITY,
        rejection(
            patch(List.of(new BookingOpeningHoursEntry(2, "23:59", "24:00"))).merge(current)));
    BookingSchedulingSettings oneMinute =
        new BookingSchedulingSettings.Patch(
                1L,
                List.of(new BookingOpeningHoursEntry(2, "23:59", "24:00")),
                null,
                null,
                null,
                null)
            .merge(current);
    assertDoesNotThrow(() -> BookingSettingsValidation.requireValid(oneMinute));
  }
}
