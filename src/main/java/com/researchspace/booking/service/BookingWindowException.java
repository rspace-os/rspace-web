package com.researchspace.booking.service;

/** The supplied booking interval is incomplete or not increasing. */
public final class BookingWindowException extends RuntimeException {
  public BookingWindowException() {
    super("errors.api.v2.booking.window");
  }
}
