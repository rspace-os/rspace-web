package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingTimezoneMode;

/** Owns resolved Booking display preferences and their persisted user override. */
public interface BookingDisplayPreferencesManager {

  record ResolvedBookingDisplayPreferences(
      String availabilityWindowStart,
      String availabilityWindowEnd,
      BookingTimezoneMode timezoneMode,
      String customTimezone,
      String institutionTimezone,
      boolean overridden) {}

  /** Returns the subject's override or the current global Booking display defaults. */
  ResolvedBookingDisplayPreferences get(User subject, User actor);

  /** Replaces the subject's complete explicit Booking display preference document. */
  ResolvedBookingDisplayPreferences replace(
      BookingDisplaySettings settings, User subject, User actor);

  /** Removes the logical override so subsequent reads inherit current global defaults. */
  void reset(User subject, User actor);
}
