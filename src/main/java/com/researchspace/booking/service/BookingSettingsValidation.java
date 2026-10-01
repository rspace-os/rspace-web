package com.researchspace.booking.service;

import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.util.List;

final class BookingSettingsValidation {

  private BookingSettingsValidation() {}

  /**
   * Validates one complete, merged settings value, including the rules that span the shared
   * interval, the open weekdays and their exceptions.
   */
  static void requireValid(BookingSchedulingSettings settings) {
    requireValid(
        settings.slotGranularityMinutes(),
        settings.openingHours(),
        settings.bufferBeforeMinutes(),
        settings.bufferAfterMinutes(),
        settings.maxBookingDurationMinutes());
    if (!BookingSchedulingSettings.areOpenDaysValid(settings.openDays())) {
      throw new InvalidBookingSchedulingSettingsException(Reason.OPEN_DAYS);
    }
    if (!BookingSchedulingSettings.areOpeningExceptionsValid(settings.openingExceptions())
        || !BookingSchedulingSettings.areOpeningExceptionsOnOpenDays(
            settings.openingExceptions(), settings.openDays())) {
      throw new InvalidBookingSchedulingSettingsException(Reason.OPENING_EXCEPTIONS);
    }
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
