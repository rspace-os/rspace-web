package com.researchspace.booking.service;

public final class InvalidBookingSchedulingSettingsException extends RuntimeException {

  public enum Reason {
    GRANULARITY("errors.api.v2.bookingConfiguration.granularity.invalid"),
    OPENING_HOURS("errors.api.v2.bookingConfiguration.openingHours.invalid"),
    BUFFER("errors.api.v2.bookingConfiguration.buffer.invalid"),
    MAXIMUM_DURATION("errors.api.v2.bookingConfiguration.maximumDuration.invalid");

    private final String errorCode;

    Reason(String errorCode) {
      this.errorCode = errorCode;
    }

    public String errorCode() {
      return errorCode;
    }
  }

  private final Reason reason;

  public InvalidBookingSchedulingSettingsException(Reason reason) {
    super(reason.errorCode());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
