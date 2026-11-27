package com.researchspace.booking.service;

import static com.researchspace.model.booking.BookingSchedulingSettings.MAX_BOOKING_DURATION_MINUTES;
import static com.researchspace.model.booking.BookingSchedulingSettings.MINUTES_PER_DAY;

public final class BookingDurationException extends RuntimeException {

  public BookingDurationException() {
    super("errors.api.v2.booking.duration");
  }

  /** The absolute limit its message states: elapsed minutes, then the same as days. */
  public static Object[] messageArguments() {
    return new Object[] {
      MAX_BOOKING_DURATION_MINUTES, MAX_BOOKING_DURATION_MINUTES / MINUTES_PER_DAY
    };
  }
}
