package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingSchedulingSettingsTest {

  @Test
  void defaultsContainSevenFullDaysAndKeepTheInitialGranularity() {
    BookingConfiguration configuration = new BookingConfiguration();
    BookingConfigurationDefaults defaults = new BookingConfigurationDefaults();
    assertEquals(5, configuration.getSlotGranularityMinutes());
    assertEquals(BookingSchedulingSettings.DEFAULT_OPENING_HOURS, configuration.getOpeningHours());
    assertEquals(configuration.getOpeningHours(), defaults.getOpeningHours());
  }

  @Test
  void patchKeepsOmittedValuesAndReplacesTheCompleteWeekInOrder() {
    BookingSchedulingSettings current = BookingSchedulingSettings.from(new BookingConfiguration());
    assertTrue(BookingSchedulingSettings.Patch.empty().isEmpty());
    assertEquals(current, BookingSchedulingSettings.Patch.empty().merge(current));
    BookingSchedulingSettings.Patch patch =
        new BookingSchedulingSettings.Patch(
            null,
            List.of(
                new BookingOpeningHoursEntry(5, "10:00", "12:00"),
                new BookingOpeningHoursEntry(1, "08:00", "18:00")),
            null,
            null,
            null,
            null);
    BookingSchedulingSettings merged = patch.merge(current);
    assertFalse(patch.isEmpty());
    assertEquals(current.slotGranularityMinutes(), merged.slotGranularityMinutes());
    assertEquals(
        Optional.of(new BookingSchedulingSettings.DailyHours("08:00", "18:00")),
        merged.effectiveHours(1));
    assertTrue(merged.effectiveHours(2).isEmpty());
    assertEquals(1, merged.openingHours().get(0).dayOfWeek());
    BookingConfiguration configuration = new BookingConfiguration();
    BookingConfigurationDefaults defaults = new BookingConfigurationDefaults();
    merged.applyTo(configuration);
    merged.applyTo(defaults);
    assertEquals(merged.openingHours(), configuration.getOpeningHours());
    assertEquals(merged.openingHours(), defaults.getOpeningHours());
    assertTrue(configuration.isOpeningHoursValid());
    configuration.setOpeningHours(List.of());
    assertFalse(configuration.isOpeningHoursValid());
  }

  @Test
  void entitySchedulesCopySuppliedListsAndChangingOneDayKeepsTheOthers() {
    ArrayList<BookingOpeningHoursEntry> supplied =
        new ArrayList<>(BookingSchedulingSettings.DEFAULT_OPENING_HOURS);
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setOpeningHours(supplied);
    supplied.set(0, new BookingOpeningHoursEntry(1, "09:00", "17:00"));
    assertEquals(BookingSchedulingSettings.DEFAULT_OPENING_HOURS, configuration.getOpeningHours());
    configuration.setOpeningHours(supplied);
    assertEquals(
        new BookingOpeningHoursEntry(1, "09:00", "17:00"), configuration.getOpeningHours().get(0));
    assertEquals(
        BookingSchedulingSettings.DEFAULT_OPENING_HOURS.subList(1, 7),
        configuration.getOpeningHours().subList(1, 7));
  }
}
