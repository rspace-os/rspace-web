package com.researchspace.booking.service;

/** A cancellation reason was supplied without requesting the terminal cancelled state. */
public final class BookingCancellationReasonRequiresCancelException
    extends IllegalArgumentException {

  public BookingCancellationReasonRequiresCancelException() {
    super("errors.api.v2.booking.cancellationReason.requiresCancel");
  }
}
