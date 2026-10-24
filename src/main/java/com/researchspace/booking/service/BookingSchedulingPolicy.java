package com.researchspace.booking.service;

import com.researchspace.booking.service.BookingPolicyException.Reason;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.BookingSchedulingSettings.DayIncrement;
import com.researchspace.model.booking.BookingSchedulingSettings.ElapsedIncrement;
import com.researchspace.model.booking.BookingSchedulingSettings.IncrementRule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * Scheduling policy shared by booking creation and time edits. Every local date and time is read in
 * the configuration's scheduling timezone.
 */
@Component
public final class BookingSchedulingPolicy {

  record ConflictInterval(Date start, Date end) {}

  /**
   * Validates whole-minute endpoints, the time increment and its start alignment, the item maximum
   * and opening coverage, then returns the conflict interval.
   */
  public ConflictInterval validate(BookingConfiguration configuration, Date start, Date end) {
    ZoneId zone = ZoneId.of(configuration.getTimeZone());
    Instant startInstant = start.toInstant();
    Instant endInstant = end.toInstant();
    ZonedDateTime localStart = startInstant.atZone(zone);
    ZonedDateTime localEnd = endInstant.atZone(zone);
    requireWholeMinute(localStart);
    requireWholeMinute(localEnd);
    BookingSchedulingSettings settings = BookingSchedulingSettings.from(configuration);
    IncrementRule rule = settings.incrementRule();
    if (rule instanceof DayIncrement days) {
      requireDayIncrement(localStart, localEnd, days.days());
    } else if (rule instanceof ElapsedIncrement elapsed) {
      requireElapsedIncrement(localStart, startInstant, endInstant, elapsed);
    }
    requireMaximumDuration(startInstant, endInstant, configuration.getMaxBookingDurationMinutes());
    requireOpeningCoverage(settings, startInstant, endInstant, zone);
    return conflictInterval(configuration, startInstant, endInstant);
  }

  /**
   * Returns the buffer-expanded conflict interval for maintenance, which is exempt from the
   * increment.
   */
  public ConflictInterval validateMaintenance(
      BookingConfiguration configuration, Date start, Date end) {
    // Exempt from the increment, opening hours and maximum, but times are still whole minutes.
    ZoneId zone = ZoneId.of(configuration.getTimeZone());
    Instant startInstant = start.toInstant();
    Instant endInstant = end.toInstant();
    requireWholeMinute(startInstant.atZone(zone));
    requireWholeMinute(endInstant.atZone(zone));
    return conflictInterval(configuration, startInstant, endInstant);
  }

  private static ConflictInterval conflictInterval(
      BookingConfiguration configuration, Instant start, Instant end) {
    long sharedGapMinutes =
        Math.max(configuration.getBufferBeforeMinutes(), configuration.getBufferAfterMinutes());
    return new ConflictInterval(
        Date.from(start.minus(sharedGapMinutes, ChronoUnit.MINUTES)),
        Date.from(end.plus(sharedGapMinutes, ChronoUnit.MINUTES)));
  }

  private static void requireWholeMinute(ZonedDateTime endpoint) {
    if (endpoint.getSecond() != 0 || endpoint.getNano() != 0) {
      throw new BookingPolicyException(Reason.GRANULARITY);
    }
  }

  /**
   * An aligned start sits on the increment's grid from local midnight; the end only has to follow a
   * positive whole number of increments in elapsed time, so it may be off the grid.
   */
  private static void requireElapsedIncrement(
      ZonedDateTime localStart, Instant start, Instant end, ElapsedIncrement increment) {
    if (increment.aligned()
        && localStart.toLocalTime().toSecondOfDay() / 60 % increment.minutes() != 0) {
      throw new BookingPolicyException(Reason.ALIGNMENT);
    }
    long elapsedMinutes = Duration.between(start, end).toMinutes();
    if (elapsedMinutes <= 0 || elapsedMinutes % increment.minutes() != 0) {
      throw new BookingPolicyException(Reason.GRANULARITY);
    }
  }

  /**
   * A day booking ends at its start's local time, a positive multiple of {@code days} local dates
   * later. When the end's date is right but the clocks skip the start's time on it, names that
   * missing local end instead of reporting a plain increment failure.
   */
  private static void requireDayIncrement(
      ZonedDateTime localStart, ZonedDateTime localEnd, long days) {
    LocalTime startTime = localStart.toLocalTime();
    LocalDate endDate = localEnd.toLocalDate();
    long dates = ChronoUnit.DAYS.between(localStart.toLocalDate(), endDate);
    boolean wholeIncrements = dates > 0 && dates % days == 0;
    if (wholeIncrements && localEnd.toLocalTime().equals(startTime)) {
      return;
    }
    ZoneId zone = localStart.getZone();
    if (wholeIncrements
        && zone.getRules().getValidOffsets(LocalDateTime.of(endDate, startTime)).isEmpty()) {
      throw BookingPolicyException.dayEndMissing(startTime, endDate, zone);
    }
    throw new BookingPolicyException(Reason.GRANULARITY);
  }

  private static void requireMaximumDuration(Instant start, Instant end, long maximumMinutes) {
    if (maximumMinutes > 0
        && Duration.between(start, end).compareTo(Duration.ofMinutes(maximumMinutes)) > 0) {
      throw BookingPolicyException.maximumDuration(maximumMinutes);
    }
  }

  /**
   * Requires every instant of {@code [start, end)} to fall in its scheduling-zone day's effective
   * opening hours. Walks calendar dates, so the number of iterations is bounded by the absolute
   * booking-length limit that callers enforce before scheduling validation.
   */
  private static void requireOpeningCoverage(
      BookingSchedulingSettings settings, Instant start, Instant end, ZoneId zone) {
    LocalDate firstDate = start.atZone(zone).toLocalDate();
    LocalDate lastDate = end.minusNanos(1).atZone(zone).toLocalDate();
    Instant cursor = start;
    for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
      BookingSchedulingSettings.DailyHours hours =
          settings
              .effectiveHours(date.getDayOfWeek().getValue())
              .orElseThrow(() -> new BookingPolicyException(Reason.OPENING_HOURS));
      Instant openingStart = date.atTime(LocalTime.parse(hours.start())).atZone(zone).toInstant();
      Instant openingEnd = openingEnd(hours.end(), date, zone);
      if (openingStart.isAfter(cursor) || !openingEnd.isAfter(cursor)) {
        throw new BookingPolicyException(Reason.OPENING_HOURS);
      }
      cursor = openingEnd;
      if (!cursor.isBefore(end)) {
        return;
      }
    }
    throw new BookingPolicyException(Reason.OPENING_HOURS);
  }

  static Instant openingEnd(String end, LocalDate date, ZoneId zone) {
    if (BookingSchedulingSettings.END_OF_DAY.equals(end)) {
      return date.plusDays(1).atStartOfDay(zone).toInstant();
    }
    return date.atTime(LocalTime.parse(end)).atZone(zone).toInstant();
  }
}
