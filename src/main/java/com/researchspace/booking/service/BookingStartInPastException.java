package com.researchspace.booking.service;

/** The booking start is not in the future. */
public final class BookingStartInPastException extends RuntimeException {
  public BookingStartInPastException() {
    super("errors.api.v2.booking.startInPast");
  }
}
