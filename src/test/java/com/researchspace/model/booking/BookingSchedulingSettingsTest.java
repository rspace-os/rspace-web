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
    assertEquals(15, configuration.getSlotGranularityMinutes());
    assertEquals(BookingIncrementUnit.MINUTES, configuration.getIncrementUnit());
    assertTrue(configuration.isAlignToIncrement());
    assertEquals(BookingIncrementUnit.MINUTES, defaults.getIncrementUnit());
    assertTrue(defaults.isAlignToIncrement());
    assertEquals(BookingSchedulingSettings.DEFAULT_OPENING_HOURS, configuration.getOpeningHours());
    assertEquals(configuration.getOpeningHours(), defaults.getOpeningHours());
  }

  @Test
  void patchKeepsOmittedValuesAndReplacesTheCompleteWeekInOrder() {
    BookingSchedulingSettings current = increment(1440, BookingIncrementUnit.HOURS, false);
    assertTrue(BookingSchedulingSettings.Patch.empty().isEmpty());
    assertEquals(current, BookingSchedulingSettings.Patch.empty().merge(current));
    BookingSchedulingSettings.Patch patch =
        new BookingSchedulingSettings.Patch(
            null,
            BookingIncrementUnit.MINUTES,
            true,
            List.of(
                new BookingOpeningHoursEntry(5, "10:00", "12:00"),
                new BookingOpeningHoursEntry(1, "08:00", "18:00")),
            null,
            null,
            null,
            null);
    BookingSchedulingSettings merged = patch.merge(current);
    assertFalse(patch.isEmpty());
    assertEquals(1440, merged.slotGranularityMinutes());
    assertEquals(BookingIncrementUnit.MINUTES, merged.incrementUnit());
    assertTrue(merged.alignToIncrement());
    assertEquals(
        Optional.of(new BookingSchedulingSettings.DailyHours("08:00", "18:00")),
        merged.effectiveHours(1));
    assertEquals(Optional.empty(), merged.effectiveHours(2));
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

  @Test
  void alignmentIsEffectiveOnlyForElapsedIncrementsShorterThanADay() {
    assertTrue(increment(1439, BookingIncrementUnit.MINUTES, true).effectiveAlignment());
    assertTrue(increment(90, BookingIncrementUnit.HOURS, true).effectiveAlignment());
    assertFalse(increment(60, BookingIncrementUnit.MINUTES, false).effectiveAlignment());
    assertFalse(increment(1440, BookingIncrementUnit.MINUTES, true).effectiveAlignment());
    assertFalse(increment(1440, BookingIncrementUnit.HOURS, true).effectiveAlignment());
    assertFalse(increment(1440, BookingIncrementUnit.DAYS, true).effectiveAlignment());

    BookingConfiguration configuration = new BookingConfiguration();
    increment(1440, BookingIncrementUnit.HOURS, true).applyTo(configuration);
    BookingSchedulingSettings stored = BookingSchedulingSettings.from(configuration);
    assertTrue(stored.alignToIncrement());
    assertFalse(stored.effectiveAlignment());
    assertTrue(
        new BookingSchedulingSettings.Patch(60L, null, null, null, null, null)
            .merge(stored)
            .effectiveAlignment());
  }

  @Test
  void theIncrementRuleNeverTreatsDaysAsElapsedMinutes() {
    assertEquals(
        new BookingSchedulingSettings.DayIncrement(2),
        increment(2880, BookingIncrementUnit.DAYS, true).incrementRule());
    assertEquals(
        new BookingSchedulingSettings.ElapsedIncrement(1440, false),
        increment(1440, BookingIncrementUnit.HOURS, true).incrementRule());
    assertEquals(
        new BookingSchedulingSettings.ElapsedIncrement(90, true),
        increment(90, BookingIncrementUnit.HOURS, true).incrementRule());
  }

  @Test
  void incrementAndMaximumValidityDependOnTheUnit() {
    assertTrue(BookingSchedulingSettings.isGranularityValid(90, BookingIncrementUnit.HOURS));
    assertTrue(BookingSchedulingSettings.isGranularityValid(1440, BookingIncrementUnit.DAYS));
    assertTrue(BookingSchedulingSettings.isGranularityValid(527_040, BookingIncrementUnit.DAYS));
    assertFalse(BookingSchedulingSettings.isGranularityValid(720, BookingIncrementUnit.DAYS));
    assertFalse(BookingSchedulingSettings.isGranularityValid(2160, BookingIncrementUnit.DAYS));
    assertFalse(BookingSchedulingSettings.isGranularityValid(15, null));
    assertTrue(
        BookingSchedulingSettings.isMaximumDurationValid(1500, 1440, BookingIncrementUnit.DAYS));
    assertFalse(
        BookingSchedulingSettings.isMaximumDurationValid(1380, 1440, BookingIncrementUnit.DAYS));
    assertFalse(
        BookingSchedulingSettings.isMaximumDurationValid(1500, 1440, BookingIncrementUnit.HOURS));
    assertFalse(
        BookingSchedulingSettings.isMaximumDurationValid(527_041, 1440, BookingIncrementUnit.DAYS));
    assertTrue(BookingSchedulingSettings.isMaximumDurationValid(0, 1440, null));

    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setIncrementUnit(BookingIncrementUnit.DAYS);
    configuration.setSlotGranularityMinutes(1440);
    configuration.setMaxBookingDurationMinutes(1500);
    assertTrue(configuration.isGranularityValid());
    assertTrue(configuration.isMaximumDurationValid());
    configuration.setSlotGranularityMinutes(720);
    assertFalse(configuration.isGranularityValid());
  }

  private static BookingSchedulingSettings increment(
      long minutes, BookingIncrementUnit unit, boolean aligned) {
    return new BookingSchedulingSettings(
        minutes, unit, aligned, BookingSchedulingSettings.DEFAULT_OPENING_HOURS, 0, 0, 0, false);
  }
}
