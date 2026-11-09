package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingSettingsValidationTest {

  private final BookingSchedulingSettings current =
      BookingSchedulingSettings.from(new BookingConfiguration());

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
    assertEquals(Reason.OPENING_HOURS, rejection(List.of()));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(
            List.of(
                new BookingOpeningHoursEntry(1, "09:00", "12:00"),
                new BookingOpeningHoursEntry(1, "13:00", "17:00"))));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(List.of(new BookingOpeningHoursEntry(0, "09:00", "17:00"))));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(Arrays.asList(new BookingOpeningHoursEntry(1, "09:00", "17:00"), null)));
    assertEquals(
        Reason.OPENING_HOURS, rejection(List.of(new BookingOpeningHoursEntry(1, null, "17:00"))));
    assertEquals(
        Reason.OPENING_HOURS,
        rejection(List.of(new BookingOpeningHoursEntry(1, "18:00", "08:00"))));
  }

  private Reason rejection(List<BookingOpeningHoursEntry> hours) {
    BookingSchedulingSettings settings = patch(hours).merge(current);
    return assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> BookingSettingsValidation.requireValid(settings))
        .reason();
  }

  private static BookingSchedulingSettings.Patch patch(List<BookingOpeningHoursEntry> hours) {
    return new BookingSchedulingSettings.Patch(null, hours, null, null, null, null);
  }
}
