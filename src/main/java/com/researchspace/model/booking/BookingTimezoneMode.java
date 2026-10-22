package com.researchspace.model.booking;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Selects how a Booking display timezone is resolved for a viewer. */
public enum BookingTimezoneMode {
  BROWSER,
  INSTITUTION,
  CUSTOM;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static BookingTimezoneMode fromName(String name) {
    return name == null ? null : valueOf(name);
  }
}
