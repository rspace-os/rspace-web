package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingTimezoneMode;
import java.util.Optional;

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

  /** Returns display preferences when Booking is available to the notification recipient. */
  Optional<ResolvedBookingDisplayPreferences> getForNotificationRecipient(User recipient);

  /**
   * Resolves display preferences from a recipient whose preferences were loaded in the current
   * transaction, without consulting the user-preference cache.
   */
  ResolvedBookingDisplayPreferences resolveForNotificationSnapshot(User recipient);

  /** Replaces the subject's complete explicit Booking display preference document. */
  ResolvedBookingDisplayPreferences replace(
      BookingDisplaySettings settings, User subject, User actor);

  /** Removes the logical override so subsequent reads inherit current global defaults. */
  void reset(User subject, User actor);
}
