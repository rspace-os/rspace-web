package com.researchspace.booking.service;

/** The supplied interval overlaps an existing confirmed booking. */
public final class BookingOverlapException extends RuntimeException {
  public BookingOverlapException() {
    super("errors.api.v2.booking.overlap");
  }
}
