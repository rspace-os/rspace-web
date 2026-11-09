package com.researchspace.model.booking;

import io.swagger.v3.oas.annotations.media.Schema;

/** Lifecycle state of one persisted booking occurrence. */
@Schema(enumAsRef = true)
public enum BookingState {
  CONFIRMED,
  CANCELLED
}
