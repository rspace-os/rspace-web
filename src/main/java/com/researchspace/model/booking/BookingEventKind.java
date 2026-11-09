package com.researchspace.model.booking;

import io.swagger.v3.oas.annotations.media.Schema;

/** Immutable kind of a persisted calendar event. */
@Schema(enumAsRef = true)
public enum BookingEventKind {
  BOOKING,
  MAINTENANCE
}
