package com.researchspace.booking.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.OptionalLong;

/** Booking interval rejected by the current configuration's scheduling policy. */
public final class BookingPolicyException extends RuntimeException {

  public enum Reason {
    GRANULARITY("errors.api.v2.booking.granularity"),
    DAY_END_MISSING("errors.api.v2.booking.dayEndMissing"),
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

  /** The local end a day increment requires, which does not exist on its date in {@code zone}. */
  public record MissingDayEnd(LocalTime time, LocalDate date, ZoneId zone) {}

  private final Reason reason;
  private final Long maximumDurationMinutes;
  private final MissingDayEnd missingDayEnd;

  /**
   * Rejects an interval for a reason that has no parameters; see {@link #maximumDuration} and
   * {@link #dayEndMissing}.
   */
  public BookingPolicyException(Reason reason) {
    this(reason, null, null);
    if (reason == Reason.MAXIMUM_DURATION) {
      throw new IllegalArgumentException("A maximum-duration failure requires its maximum");
    }
    if (reason == Reason.DAY_END_MISSING) {
      throw new IllegalArgumentException("A missing day end requires its local end and zone");
    }
  }

  private BookingPolicyException(
      Reason reason, Long maximumDurationMinutes, MissingDayEnd missingDayEnd) {
    super(reason.errorCode());
    this.reason = reason;
    this.maximumDurationMinutes = maximumDurationMinutes;
    this.missingDayEnd = missingDayEnd;
  }

  /** The interval is longer than the bookable item's configured maximum. */
  public static BookingPolicyException maximumDuration(long maximumDurationMinutes) {
    return new BookingPolicyException(Reason.MAXIMUM_DURATION, maximumDurationMinutes, null);
  }

  /**
   * A day-increment booking would end at {@code time} on {@code date}, but that local time does not
   * exist there in {@code zone} because the clocks skip it.
   */
  public static BookingPolicyException dayEndMissing(LocalTime time, LocalDate date, ZoneId zone) {
    return new BookingPolicyException(
        Reason.DAY_END_MISSING, null, new MissingDayEnd(time, date, zone));
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

  /** The missing local end, present only for {@link Reason#DAY_END_MISSING}. */
  public Optional<MissingDayEnd> missingDayEnd() {
    return Optional.ofNullable(missingDayEnd);
  }
}
