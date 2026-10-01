package com.researchspace.booking.service;

import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingSchedulingSettings;

final class BookingSettingsValidation {

  private BookingSettingsValidation() {}

  static void requireValid(BookingSchedulingSettings settings) {
    requireValid(
        settings.slotGranularityMinutes(),
        settings.openingStart(),
        settings.openingEnd(),
        settings.bufferBeforeMinutes(),
        settings.bufferAfterMinutes(),
        settings.maxBookingDurationMinutes());
  }

  static void requireValid(
      long granularity,
      String openingStart,
      String openingEnd,
      long before,
      long after,
      long maximumDuration) {
    if (!BookingSchedulingSettings.isGranularityValid(granularity)) {
      throw new InvalidBookingSchedulingSettingsException(Reason.GRANULARITY);
    }
    if (!BookingSchedulingSettings.areOpeningHoursValid(openingStart, openingEnd)) {
      throw new InvalidBookingSchedulingSettingsException(Reason.OPENING_HOURS);
    }
    if (!BookingSchedulingSettings.isBufferValid(before)
        || !BookingSchedulingSettings.isBufferValid(after)) {
      throw new InvalidBookingSchedulingSettingsException(Reason.BUFFER);
    }
    if (!BookingSchedulingSettings.isMaximumDurationValid(maximumDuration, granularity)) {
      throw new InvalidBookingSchedulingSettingsException(Reason.MAXIMUM_DURATION);
    }
  }
}
