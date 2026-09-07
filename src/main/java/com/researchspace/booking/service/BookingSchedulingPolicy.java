package com.researchspace.booking.service;

import com.researchspace.model.booking.BookingConfiguration;
import java.util.Date;

/** Validates one booking interval and computes its effective conflict interval. */
public interface BookingSchedulingPolicy {

  record ConflictInterval(Date start, Date end) {}

  /** Validates granularity, duration, and opening coverage, then returns the conflict interval. */
  ConflictInterval validate(BookingConfiguration configuration, Date start, Date end);

  /** Validates maintenance granularity and returns its buffer-expanded conflict interval. */
  ConflictInterval validateMaintenance(BookingConfiguration configuration, Date start, Date end);
}
