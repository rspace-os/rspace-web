package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingSchedulingSettings;

/** Reads and atomically patches the global defaults copied into new bookable items. */
public interface BookingConfigurationDefaultsManager {

  /** Returns the required singleton row for any authenticated actor. */
  BookingConfigurationDefaults getDefaults(User actor);

  /** Applies one versioned patch as a sysadmin and publishes an audit event. */
  BookingConfigurationDefaults updateDefaults(
      BookingSchedulingSettings.Patch patch, long expectedVersion, User subject, User actor);
}
