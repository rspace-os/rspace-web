package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingOpeningException;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingSettingsValidationTest {

  private static final BookingSchedulingSettings CURRENT =
      new BookingSchedulingSettings(
          5,
          "09:00",
          "17:00",
          List.of(1, 2, 3, 4, 5, 6),
          List.of(new BookingOpeningException(6, "10:00", "16:00")),
          0,
          0,
          0,
          false);

  private static Reason rejection(BookingSchedulingSettings.Patch patch) {
    return assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> BookingSettingsValidation.requireValid(patch.merge(CURRENT)))
        .reason();
  }

  private static BookingSchedulingSettings.Patch days(List<Integer> openDays) {
    return new BookingSchedulingSettings.Patch(
        null, null, null, openDays, null, null, null, null, null);
  }

  private static BookingSchedulingSettings.Patch exceptions(
      List<BookingOpeningException> exceptions) {
    return new BookingSchedulingSettings.Patch(
        null, null, null, null, exceptions, null, null, null, null);
  }

  @Test
  void acceptsSharedHoursWithExceptionsOnOpenDaysIncludingSharedIdenticalOnes() {
    assertDoesNotThrow(() -> BookingSettingsValidation.requireValid(CURRENT));
    assertDoesNotThrow(
        () ->
            BookingSettingsValidation.requireValid(
                exceptions(List.of(new BookingOpeningException(1, "09:00", "17:00")))
                    .merge(CURRENT)));
  }

  @Test
  void acceptsClosingAtMidnightAfterAnyStartInSharedHoursAndExceptions() {
    assertDoesNotThrow(
        () ->
            BookingSettingsValidation.requireValid(
                new BookingSchedulingSettings.Patch(
                        null, "18:00", "24:00", null, null, null, null, null, null)
                    .merge(CURRENT)));
    assertDoesNotThrow(
        () ->
            BookingSettingsValidation.requireValid(
                exceptions(List.of(new BookingOpeningException(1, "18:00", "24:00")))
                    .merge(CURRENT)));
    assertDoesNotThrow(
        () ->
            BookingSettingsValidation.requireValid(
                exceptions(List.of(new BookingOpeningException(2, "23:59", "24:00")))
                    .merge(CURRENT)));
  }

  @Test
  void checksAPartialPatchAgainstTheMergedSettings() {
    assertEquals(Reason.OPENING_EXCEPTIONS, rejection(days(List.of(1, 2, 3, 4, 5))));
    assertDoesNotThrow(
        () ->
            BookingSettingsValidation.requireValid(
                new BookingSchedulingSettings.Patch(
                        null, null, null, List.of(1, 2, 3, 4, 5), List.of(), null, null, null, null)
                    .merge(CURRENT)));
    assertEquals(
        Reason.OPENING_EXCEPTIONS,
        rejection(exceptions(List.of(new BookingOpeningException(7, "10:00", "16:00")))));
  }

  @Test
  void rejectsInvalidDaySelections() {
    assertEquals(Reason.OPEN_DAYS, rejection(days(List.of())));
    assertEquals(Reason.OPEN_DAYS, rejection(days(List.of(1, 1))));
    assertEquals(Reason.OPEN_DAYS, rejection(days(List.of(0, 1))));
    assertEquals(Reason.OPEN_DAYS, rejection(days(List.of(8))));
    assertEquals(Reason.OPEN_DAYS, rejection(days(Arrays.asList(1, null))));
    assertEquals(Reason.OPEN_DAYS, rejection(days(List.of(1, 2, 3, 4, 5, 6, 7, 1))));
  }

  @Test
  void rejectsInvalidExceptionsAndKeepsTheSharedIntervalRule() {
    assertEquals(
        Reason.OPENING_EXCEPTIONS,
        rejection(exceptions(List.of(new BookingOpeningException(1, "17:00", "09:00")))));
    assertEquals(
        Reason.OPENING_EXCEPTIONS,
        rejection(exceptions(List.of(new BookingOpeningException(1, "24:00", "24:00")))));
    assertEquals(
        Reason.OPENING_EXCEPTIONS,
        rejection(
            exceptions(
                List.of(
                    new BookingOpeningException(1, "09:00", "12:00"),
                    new BookingOpeningException(1, "13:00", "17:00")))));
    assertEquals(
        Reason.OPENING_EXCEPTIONS,
        rejection(exceptions(Arrays.asList((BookingOpeningException) null))));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(
            new BookingSchedulingSettings.Patch(
                null, "18:00", "08:00", null, null, null, null, null, null)));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(
            new BookingSchedulingSettings.Patch(
                null, "18:00", "00:00", null, null, null, null, null, null)));
  }
}
