package com.researchspace.booking.service;

import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingSchedulingSettings;

final class BookingSettingsValidation {

  private BookingSettingsValidation() {}

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
