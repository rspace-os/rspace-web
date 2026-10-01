package com.researchspace.booking.service;

/** Raised when a command is incompatible with the configuration's lifecycle state. */
public final class BookingConfigurationLifecycleException extends RuntimeException {

  public BookingConfigurationLifecycleException() {
    super("errors.api.v2.bookingConfiguration.lifecycleConflict");
  }
}
