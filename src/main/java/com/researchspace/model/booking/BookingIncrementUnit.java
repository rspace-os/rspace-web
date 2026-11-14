package com.researchspace.model.booking;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The unit in which a bookable item's time increment is chosen and enforced. The increment is
 * always stored as {@code slotGranularityMinutes}; the unit decides how bookings are measured
 * against it.
 */
public enum BookingIncrementUnit {
  /** Elapsed minutes. */
  MINUTES,
  /** Elapsed minutes chosen as hours; 90 minutes is 1.5 hours. */
  HOURS,
  /**
   * Calendar days in the scheduling timezone, stored as the day count times 1440. A booking ends at
   * its start's local time a whole number of increments later, however long those days are.
   */
  DAYS;

  /**
   * Reads exactly a constant's name. Jackson would otherwise read a JSON number as an ordinal, so
   * {@code 1} would become {@link #HOURS}; any other value fails, and the API rejects the request.
   */
  @JsonCreator
  public static BookingIncrementUnit fromName(String name) {
    return valueOf(name);
  }
}
