package com.researchspace.model.booking;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Selects the 12- or 24-hour clock that Booking uses to write times for a viewer. */
public enum BookingTimeFormat {
  /** The browser region's clock in the UI; the language's usual clock where there is no browser. */
  AUTOMATIC,
  /** A 12-hour clock with a day period, such as 2:30 PM. */
  H12,
  /** A 24-hour clock from 00:00 to 23:59, such as 14:30. */
  H24;

  /**
   * Reads exactly a constant's name. Jackson would otherwise read a JSON number as an ordinal, so
   * {@code 1} would become {@link #H12}; any other value fails, and the API rejects the request.
   */
  @JsonCreator
  public static BookingTimeFormat fromName(String name) {
    return valueOf(name);
  }

  /** The Unicode {@code hc} keyword for an explicit clock, or null for {@link #AUTOMATIC}. */
  public String hourCycleKeyword() {
    return switch (this) {
      case AUTOMATIC -> null;
      case H12 -> "h12";
      case H24 -> "h23";
    };
  }
}
