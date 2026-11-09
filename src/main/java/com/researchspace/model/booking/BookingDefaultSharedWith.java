package com.researchspace.model.booking;

import com.fasterxml.jackson.annotation.JsonCreator;

/** The source of initial Booker assignments for a new booking configuration. */
public enum BookingDefaultSharedWith {
  ALL_USERS,
  SELECTED,
  ONLY_ME;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static BookingDefaultSharedWith fromName(String name) {
    return name == null ? null : valueOf(name);
  }
}
