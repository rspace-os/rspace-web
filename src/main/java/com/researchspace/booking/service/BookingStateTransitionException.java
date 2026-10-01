package com.researchspace.booking.service;

/** The requested booking lifecycle transition is not permitted. */
public final class BookingStateTransitionException extends RuntimeException {
  public BookingStateTransitionException() {
    super("errors.api.v2.booking.state.transition");
  }
}
