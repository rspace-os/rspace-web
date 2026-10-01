package com.researchspace.booking.service;

import java.util.Objects;

/** The requested interval itself overlaps an existing confirmed booking or maintenance event. */
public final class BookingOverlapException extends RuntimeException {

  private final transient ConflictingEvent conflict;

  public BookingOverlapException(ConflictingEvent conflict) {
    super("errors.api.v2.booking.overlap");
    this.conflict = Objects.requireNonNull(conflict, "Conflicting event");
  }

  /** The first existing event that overlaps the requested interval. */
  public ConflictingEvent conflict() {
    return conflict;
  }
}
