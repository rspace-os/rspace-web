package com.researchspace.booking.service;

public final class BookingDurationException extends RuntimeException {

  public BookingDurationException() {
    super("errors.api.v2.booking.duration");
  }
}
