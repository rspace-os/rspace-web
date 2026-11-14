package com.researchspace.booking.service;

import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.TimeSlotBooking;
import java.time.Instant;
import java.util.Objects;

/**
 * The public identity and interval of an event that blocks a requested booking interval.
 *
 * <p>It deliberately carries no purpose, requester, or other detail the caller might not be allowed
 * to read.
 */
public record ConflictingEvent(long id, BookingEventKind kind, Instant start, Instant end) {

  public ConflictingEvent {
    Objects.requireNonNull(kind, "Conflicting event kind");
    Objects.requireNonNull(start, "Conflicting event start");
    Objects.requireNonNull(end, "Conflicting event end");
  }

  /** Copies only the public fields of a persisted booking or maintenance event. */
  public static ConflictingEvent of(TimeSlotBooking event) {
    return new ConflictingEvent(
        event.getId(),
        event.getKind(),
        event.getStartTime().toInstant(),
        event.getEndTime().toInstant());
  }
}
