package com.researchspace.booking.service;

/** Indicates that a Booking display window or timezone selection is invalid. */
public final class InvalidBookingDisplaySettingsException extends RuntimeException {

  public enum Reason {
    AVAILABILITY_WINDOW("errors.api.v2.bookingDisplayPreferences.availabilityWindow.invalid"),
    TIMEZONE("errors.api.v2.bookingDisplayPreferences.timeZone.invalid");

    private final String errorCode;

    Reason(String errorCode) {
      this.errorCode = errorCode;
    }

    public String errorCode() {
      return errorCode;
    }
  }

  private final Reason reason;

  public InvalidBookingDisplaySettingsException(Reason reason) {
    super(reason.errorCode());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
