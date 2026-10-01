package com.researchspace.booking.service;

/** Raised when Booking creation-default sharing is contradictory or contains an invalid grantee. */
public final class InvalidBookingDefaultSharingException extends RuntimeException {

  public InvalidBookingDefaultSharingException() {
    super("errors.api.v2.bookingConfiguration.defaultSharing.invalid");
  }
}
