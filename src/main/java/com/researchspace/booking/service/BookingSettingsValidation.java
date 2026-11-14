package com.researchspace.booking.service;

import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.time.ZoneId;
import java.util.Set;

final class BookingSettingsValidation {

  // Java exposes these legacy IDs even though the supported-browser matrix rejects them.
  private static final Set<String> LEGACY_BROWSER_INCOMPATIBLE_TIME_ZONES =
      Set.of("CST6CDT", "EST5EDT", "MST7MDT", "PST8PDT");

  private BookingSettingsValidation() {}

  static void requireValidTimeZone(String timeZone) {
    if (timeZone == null
        || timeZone.isBlank()
        || !ZoneId.getAvailableZoneIds().contains(timeZone)
        || timeZone.startsWith("SystemV/")
        || LEGACY_BROWSER_INCOMPATIBLE_TIME_ZONES.contains(timeZone)) {
      throw new InvalidBookingSchedulingSettingsException(Reason.TIME_ZONE);
    }
  }

  /** Validates one complete, merged settings value and whether the increment fits opening hours. */
  static void requireValid(BookingSchedulingSettings settings) {
    if (!BookingSchedulingSettings.isGranularityValid(
        settings.slotGranularityMinutes(), settings.incrementUnit())) {
      throw new InvalidBookingSchedulingSettingsException(Reason.GRANULARITY);
    }
    if (!BookingSchedulingSettings.isBufferValid(settings.bufferBeforeMinutes())
        || !BookingSchedulingSettings.isBufferValid(settings.bufferAfterMinutes())) {
      throw new InvalidBookingSchedulingSettingsException(Reason.BUFFER);
    }
    if (!BookingSchedulingSettings.isMaximumDurationValid(
        settings.maxBookingDurationMinutes(),
        settings.slotGranularityMinutes(),
        settings.incrementUnit())) {
      throw new InvalidBookingSchedulingSettingsException(Reason.MAXIMUM_DURATION);
    }
    if (!BookingSchedulingSettings.areOpeningHoursValid(settings.openingHours())) {
      throw new InvalidBookingSchedulingSettingsException(Reason.OPENING_HOURS);
    }
    if (!settings.incrementFitsOpeningHours()) {
      throw new InvalidBookingSchedulingSettingsException(Reason.GRANULARITY);
    }
  }
}
