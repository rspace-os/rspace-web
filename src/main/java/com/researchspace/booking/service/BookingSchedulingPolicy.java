package com.researchspace.booking.service;

import com.researchspace.model.booking.BookingConfiguration;
import java.util.Date;

/** Validates one booking interval and computes its effective conflict interval. */
public interface BookingSchedulingPolicy {

  record ConflictInterval(Date start, Date end) {}

  /**
   * Validates whole-minute endpoints, the time increment and its start alignment, the item maximum
   * and opening coverage, then returns the conflict interval.
   */
  ConflictInterval validate(BookingConfiguration configuration, Date start, Date end);

  /**
   * Returns the buffer-expanded conflict interval for maintenance, which is exempt from the
   * increment.
   */
  ConflictInterval validateMaintenance(BookingConfiguration configuration, Date start, Date end);
}
