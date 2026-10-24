package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BookingDisplaySettingsTest {

  @Test
  void defaultsAreEightToSixInBrowserMode() {
    BookingDisplaySettings defaults = BookingDisplaySettings.defaults();

    assertEquals("08:00", defaults.availabilityWindowStart());
    assertEquals("18:00", defaults.availabilityWindowEnd());
    assertEquals(BookingTimezoneMode.BROWSER, defaults.timezoneMode());
    assertNull(defaults.customTimezone());
    assertTrue(defaults.hasValidAvailabilityWindow());
    assertTrue(defaults.hasValidTimezoneChoice());
  }

  @Test
  void acceptsExactWindowBoundariesIncludingTwentyFourHundred() {
    assertTrue(settings("00:00", "24:00").hasValidAvailabilityWindow());
    assertTrue(settings("23:58", "23:59").hasValidAvailabilityWindow());
  }

  @Test
  void rejectsMalformedEqualReversedAndOvernightWindows() {
    assertFalse(settings("8:00", "18:00").hasValidAvailabilityWindow());
    assertFalse(settings("08:00", "08:00").hasValidAvailabilityWindow());
    assertFalse(settings("18:00", "08:00").hasValidAvailabilityWindow());
    assertFalse(settings("24:00", "24:00").hasValidAvailabilityWindow());
  }

  @Test
  void validatesAndNormalizesTimezoneSelections() {
    assertTrue(
        new BookingDisplaySettings("08:00", "18:00", BookingTimezoneMode.CUSTOM, "America/New_York")
            .hasValidTimezoneChoice());
    assertFalse(
        new BookingDisplaySettings("08:00", "18:00", BookingTimezoneMode.CUSTOM, null)
            .hasValidTimezoneChoice());
    assertFalse(
        new BookingDisplaySettings("08:00", "18:00", BookingTimezoneMode.CUSTOM, "Not/AZone")
            .hasValidTimezoneChoice());

    BookingDisplaySettings browser =
        new BookingDisplaySettings("08:00", "18:00", BookingTimezoneMode.BROWSER, "UTC");
    assertNull(browser.customTimezone());
    assertTrue(browser.hasValidTimezoneChoice());
  }

  @Test
  void patchMergesIntoOneCompleteNormalizedValue() {
    BookingDisplaySettings current =
        new BookingDisplaySettings("09:00", "17:00", BookingTimezoneMode.CUSTOM, "UTC");

    BookingDisplaySettings merged =
        new BookingDisplaySettings.Patch(
                null, "18:00", BookingTimezoneMode.INSTITUTION, "Europe/Paris")
            .merge(current);

    assertEquals("09:00", merged.availabilityWindowStart());
    assertEquals("18:00", merged.availabilityWindowEnd());
    assertEquals(BookingTimezoneMode.INSTITUTION, merged.timezoneMode());
    assertNull(merged.customTimezone());
  }

  private static BookingDisplaySettings settings(String start, String end) {
    return new BookingDisplaySettings(start, end, BookingTimezoneMode.BROWSER, null);
  }
}
