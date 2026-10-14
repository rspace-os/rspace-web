package com.researchspace.booking.service;

import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.util.List;

final class BookingSettingsValidation {

  private BookingSettingsValidation() {}

  static void requireValid(BookingSchedulingSettings settings) {
    requireValid(
        settings.slotGranularityMinutes(),
        settings.openingHours(),
        settings.bufferBeforeMinutes(),
        settings.bufferAfterMinutes(),
        settings.maxBookingDurationMinutes());
  }

  static void requireValid(
      long granularity,
      List<BookingOpeningHoursEntry> openingHours,
      long before,
      long after,
      long maximumDuration) {
    if (!BookingSchedulingSettings.isGranularityValid(granularity)) {
      throw new InvalidBookingSchedulingSettingsException(Reason.GRANULARITY);
    }
    if (!BookingSchedulingSettings.areOpeningHoursValid(openingHours)) {
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
