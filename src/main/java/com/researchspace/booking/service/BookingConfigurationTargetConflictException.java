package com.researchspace.booking.service;

/** A bookable target already has a different booking configuration. */
public final class BookingConfigurationTargetConflictException extends RuntimeException {

  @java.io.Serial private static final long serialVersionUID = 1L;

  public BookingConfigurationTargetConflictException() {
    super("errors.api.v2.bookingConfiguration.target.conflict");
  }

  public BookingConfigurationTargetConflictException(Throwable cause) {
    super("errors.api.v2.bookingConfiguration.target.conflict", cause);
  }
}
