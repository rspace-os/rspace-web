package com.researchspace.booking.service;

/** The selected target has no enabled booking configuration. */
public final class BookingTargetUnavailableException extends RuntimeException {
  public BookingTargetUnavailableException() {
    super("errors.api.v2.booking.target.unavailable");
  }
}
