package com.researchspace.booking.service;

import java.util.Objects;

/**
 * The requested interval is free, but it falls inside the configured buffer around an existing
 * confirmed booking or maintenance event.
 */
public final class BookingBufferConflictException extends RuntimeException {

  private final transient ConflictingEvent conflict;
  private final long bufferMinutes;

  public BookingBufferConflictException(ConflictingEvent conflict, long bufferMinutes) {
    super("errors.api.v2.booking.buffer");
    this.conflict = Objects.requireNonNull(conflict, "Conflicting event");
    this.bufferMinutes = bufferMinutes;
  }

  /** The first existing event whose buffer the requested interval enters. */
  public ConflictingEvent conflict() {
    return conflict;
  }

  /** Minutes that must stay free before and after a booking. */
  public long bufferMinutes() {
    return bufferMinutes;
  }
}
