package com.researchspace.booking.service;

/** Booking interval rejected by the current configuration's scheduling policy. */
public final class BookingPolicyException extends RuntimeException {

  public enum Reason {
    GRANULARITY("errors.api.v2.booking.granularity"),
    OPENING_HOURS("errors.api.v2.booking.openingHours"),
    MAXIMUM_DURATION("errors.api.v2.booking.maximumDuration");

    private final String errorCode;

    Reason(String errorCode) {
      this.errorCode = errorCode;
    }

    public String errorCode() {
      return errorCode;
    }
  }

  private final Reason reason;

  public BookingPolicyException(Reason reason) {
    super(reason.errorCode());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
