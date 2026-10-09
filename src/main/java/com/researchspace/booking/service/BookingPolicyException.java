package com.researchspace.booking.service;

import java.util.OptionalLong;

/** Booking interval rejected by the current configuration's scheduling policy. */
public final class BookingPolicyException extends RuntimeException {

  public enum Reason {
    GRANULARITY("errors.api.v2.booking.granularity"),
    OPENING_HOURS("errors.api.v2.booking.openingHours"),
    MAXIMUM_DURATION("errors.api.v2.booking.maximumDuration");

    private final String errorCode;

    Reason(String errorCode) {
      this.errorCode = errorCode;
    }

    public String errorCode() {
      return errorCode;
    }
  }

  private final Reason reason;
  private final Long maximumDurationMinutes;

  /** Rejects an interval for a reason that has no parameters; see {@link #maximumDuration}. */
  public BookingPolicyException(Reason reason) {
    this(reason, null);
    if (reason == Reason.MAXIMUM_DURATION) {
      throw new IllegalArgumentException("A maximum-duration failure requires its maximum");
    }
  }

  private BookingPolicyException(Reason reason, Long maximumDurationMinutes) {
    super(reason.errorCode());
    this.reason = reason;
    this.maximumDurationMinutes = maximumDurationMinutes;
  }

  /** The interval is longer than the bookable item's configured maximum. */
  public static BookingPolicyException maximumDuration(long maximumDurationMinutes) {
    return new BookingPolicyException(Reason.MAXIMUM_DURATION, maximumDurationMinutes);
  }

  public Reason reason() {
    return reason;
  }

  /** The configured maximum, present only for {@link Reason#MAXIMUM_DURATION}. */
  public OptionalLong maximumDurationMinutes() {
    return maximumDurationMinutes == null
        ? OptionalLong.empty()
        : OptionalLong.of(maximumDurationMinutes);
  }
}
