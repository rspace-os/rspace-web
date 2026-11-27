package com.researchspace.booking.service;

/** The supplied cancellation reason exceeds the API's 500-character limit. */
public final class BookingCancellationReasonLengthException extends IllegalArgumentException {

  public BookingCancellationReasonLengthException() {
    super("errors.api.v2.booking.cancellationReason.length");
  }
}
