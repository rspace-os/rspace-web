package com.researchspace.booking.service;

/** Raised when an event changed after the caller loaded it. */
public class BookingConcurrentModificationException extends RuntimeException {

  private static final long serialVersionUID = 1L;
}
