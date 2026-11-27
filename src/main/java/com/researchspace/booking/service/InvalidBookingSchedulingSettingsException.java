package com.researchspace.booking.service;

import static com.researchspace.model.booking.BookingSchedulingSettings.MAX_BOOKING_DURATION_MINUTES;
import static com.researchspace.model.booking.BookingSchedulingSettings.MAX_BUFFER_MINUTES;
import static com.researchspace.model.booking.BookingSchedulingSettings.MINUTES_PER_DAY;

public final class InvalidBookingSchedulingSettingsException extends RuntimeException {

  public enum Reason {
    GRANULARITY(
        "errors.api.v2.bookingConfiguration.granularity.invalid", MAX_BOOKING_DURATION_MINUTES),
    OPENING_HOURS("errors.api.v2.bookingConfiguration.openingHours.invalid"),
    BUFFER("errors.api.v2.bookingConfiguration.buffer.invalid", MAX_BUFFER_MINUTES),
    MAXIMUM_DURATION(
        "errors.api.v2.bookingConfiguration.maximumDuration.invalid",
        MAX_BOOKING_DURATION_MINUTES,
        MINUTES_PER_DAY),
    TIME_ZONE("errors.api.v2.bookingConfiguration.timeZone.invalid");

    private final String errorCode;
    private final Object[] messageArguments;

    Reason(String errorCode, Object... messageArguments) {
      this.errorCode = errorCode;
      this.messageArguments = messageArguments;
    }

    public String errorCode() {
      return errorCode;
    }

    /** The limits the error message states, formatted by the message as numbers. */
    public Object[] messageArguments() {
      return messageArguments.clone();
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
