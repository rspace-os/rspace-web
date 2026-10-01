package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingSchedulingSettingsTest {

  private static BookingSchedulingSettings settings(
      List<Integer> openDays, List<BookingOpeningException> exceptions) {
    return new BookingSchedulingSettings(5, "09:00", "17:00", openDays, exceptions, 0, 0, 0, false);
  }

  @Test
  void effectiveHoursAreClosedAnExceptionOrTheSharedInterval() {
    BookingSchedulingSettings settings =
        settings(
            List.of(1, 2, 3, 4, 5, 6), List.of(new BookingOpeningException(6, "10:00", "16:00")));

    assertEquals(
        Optional.of(new BookingSchedulingSettings.DailyHours("09:00", "17:00")),
        settings.effectiveHours(1));
    assertEquals(
        Optional.of(new BookingSchedulingSettings.DailyHours("10:00", "16:00")),
        settings.effectiveHours(6));
    assertEquals(Optional.empty(), settings.effectiveHours(7));
  }

  @Test
  void entityDefaultsOpenEveryDayWithoutExceptions() {
    BookingSchedulingSettings configuration =
        BookingSchedulingSettings.from(new BookingConfiguration());
    BookingSchedulingSettings defaults =
        BookingSchedulingSettings.from(new BookingConfigurationDefaults());

    assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), configuration.openDays());
    assertEquals(List.of(), configuration.openingExceptions());
    assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), defaults.openDays());
    assertEquals(List.of(), defaults.openingExceptions());
  }

  @Test
  void patchReplacesEachListWholeAndKeepsOmittedLists() {
    BookingSchedulingSettings current =
        settings(List.of(1, 2, 3), List.of(new BookingOpeningException(2, "10:00", "16:00")));
    BookingSchedulingSettings.Patch daysOnly =
        new BookingSchedulingSettings.Patch(
            null, null, null, List.of(1, 2), null, null, null, null, null);

    BookingSchedulingSettings merged = daysOnly.merge(current);

    assertEquals(List.of(1, 2), merged.openDays());
    assertEquals(current.openingExceptions(), merged.openingExceptions());
    assertFalse(daysOnly.isEmpty());
    assertFalse(
        new BookingSchedulingSettings.Patch(
                null, null, null, null, List.of(), null, null, null, null)
            .isEmpty());
    assertTrue(BookingSchedulingSettings.Patch.empty().isEmpty());
    assertTrue(
        new BookingSchedulingSettings.Patch(null, null, null, null, null, null, null).isEmpty());
  }

  @Test
  void applyToReplacesEntityListsSortedAndWholesale() {
    BookingConfiguration configuration = new BookingConfiguration();
    settings(
            List.of(5, 1),
            List.of(
                new BookingOpeningException(5, "10:00", "12:00"),
                new BookingOpeningException(1, "08:00", "18:00")))
        .applyTo(configuration);

    assertEquals(List.of(1, 5), configuration.getOpenDays());
    assertEquals(1, configuration.getOpeningExceptions().get(0).dayOfWeek());
    assertTrue(configuration.isOpenDaysValid());
    assertTrue(configuration.isOpeningExceptionsValid());

    configuration.setOpenDays(List.of(1));
    assertFalse(configuration.isOpeningExceptionsValid());
    configuration.setOpenDays(List.of());
    assertFalse(configuration.isOpenDaysValid());
  }
}
