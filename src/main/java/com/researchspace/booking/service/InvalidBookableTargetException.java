package com.researchspace.booking.service;

/** The resolved relationship does not identify an eligible bookable entity. */
public final class InvalidBookableTargetException extends RuntimeException {

  @java.io.Serial private static final long serialVersionUID = 1L;

  public InvalidBookableTargetException() {
    super("errors.api.v2.bookingConfiguration.target.invalid");
  }
}
