package com.researchspace.booking.service;

/** The resolved relationship does not identify an eligible bookable entity. */
public final class InvalidBookableTargetException extends RuntimeException {

  public InvalidBookableTargetException() {
    super("errors.api.v2.bookingConfiguration.target.invalid");
  }
}
