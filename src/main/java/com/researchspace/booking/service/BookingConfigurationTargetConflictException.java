package com.researchspace.booking.service;

/** A bookable target already has a different booking configuration. */
public final class BookingConfigurationTargetConflictException extends RuntimeException {

  public BookingConfigurationTargetConflictException() {
    super("errors.api.v2.bookingConfiguration.target.conflict");
  }

  public BookingConfigurationTargetConflictException(Throwable cause) {
    super("errors.api.v2.bookingConfiguration.target.conflict", cause);
  }
}
