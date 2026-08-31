package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDefaultSharedWith;
import com.researchspace.model.booking.BookingDisplaySettings;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.util.List;

/** Reads and atomically patches global Booking scheduling and display defaults. */
public interface BookingConfigurationDefaultsManager {

  /** Returns the required singleton row for any authenticated actor. */
  BookingConfigurationDefaults getDefaults(User actor);

  /** Applies one versioned patch as a sysadmin and publishes an audit event. */
  BookingConfigurationDefaults updateDefaults(
      BookingSchedulingSettings.Patch schedulingPatch,
      BookingDisplaySettings.Patch displayPatch,
      long expectedVersion,
      User subject,
      User actor);

  /** Applies scheduling, display, and initial-access defaults as one versioned update. */
  BookingConfigurationDefaults updateDefaults(
      BookingSchedulingSettings.Patch schedulingPatch,
      BookingDisplaySettings.Patch displayPatch,
      BookingDefaultSharedWith defaultSharedWith,
      List<String> selectedGranteeKeys,
      long expectedVersion,
      User subject,
      User actor);
}
