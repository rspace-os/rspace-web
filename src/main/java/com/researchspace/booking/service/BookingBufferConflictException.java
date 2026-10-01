package com.researchspace.booking.service;

import java.util.Objects;

/**
 * The requested interval is free, but it falls inside the configured buffer around an existing
 * confirmed booking or maintenance event.
 */
public final class BookingBufferConflictException extends RuntimeException {

  private final transient ConflictingEvent conflict;
  private final long bufferBeforeMinutes;
  private final long bufferAfterMinutes;

  public BookingBufferConflictException(
      ConflictingEvent conflict, long bufferBeforeMinutes, long bufferAfterMinutes) {
    super("errors.api.v2.booking.buffer");
    this.conflict = Objects.requireNonNull(conflict, "Conflicting event");
    this.bufferBeforeMinutes = bufferBeforeMinutes;
    this.bufferAfterMinutes = bufferAfterMinutes;
  }

  /** The first existing event whose buffer the requested interval enters. */
  public ConflictingEvent conflict() {
    return conflict;
  }

  /** Minutes that must stay free before another event. */
  public long bufferBeforeMinutes() {
    return bufferBeforeMinutes;
  }

  /** Minutes that must stay free after another event. */
  public long bufferAfterMinutes() {
    return bufferAfterMinutes;
  }
}
