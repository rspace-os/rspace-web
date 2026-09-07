package com.researchspace.booking.service;

public final class StaleBookingSettingsException extends RuntimeException {

  public StaleBookingSettingsException() {
    super("errors.api.v2.bookingConfiguration.stale");
  }
}
