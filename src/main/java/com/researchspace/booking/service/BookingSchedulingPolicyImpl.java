package com.researchspace.booking.service;

import com.researchspace.booking.service.BookingPolicyException.Reason;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.springframework.stereotype.Component;

/** Wall-clock scheduling policy shared by booking creation and time edits. */
@Component
public final class BookingSchedulingPolicyImpl implements BookingSchedulingPolicy {

  @Override
  public ConflictInterval validate(BookingConfiguration configuration, Date start, Date end) {
    ZoneId zone = ZoneId.of(configuration.getTimeZone());
    Instant startInstant = start.toInstant();
    Instant endInstant = end.toInstant();
    requireAligned(startInstant.atZone(zone), configuration.getSlotGranularityMinutes());
    requireAligned(endInstant.atZone(zone), configuration.getSlotGranularityMinutes());
    requireMaximumDuration(startInstant, endInstant, configuration.getMaxBookingDurationMinutes());
    requireOpeningCoverage(configuration, startInstant, endInstant, zone);
    return conflictInterval(configuration, startInstant, endInstant);
  }

  @Override
  public ConflictInterval validateMaintenance(
      BookingConfiguration configuration, Date start, Date end) {
    ZoneId zone = ZoneId.of(configuration.getTimeZone());
    Instant startInstant = start.toInstant();
    Instant endInstant = end.toInstant();
    requireAligned(startInstant.atZone(zone), configuration.getSlotGranularityMinutes());
    requireAligned(endInstant.atZone(zone), configuration.getSlotGranularityMinutes());
    return conflictInterval(configuration, startInstant, endInstant);
  }

  private static ConflictInterval conflictInterval(
      BookingConfiguration configuration, Instant start, Instant end) {
    return new ConflictInterval(
        Date.from(start.minus(configuration.getBufferAfterMinutes(), ChronoUnit.MINUTES)),
        Date.from(end.plus(configuration.getBufferBeforeMinutes(), ChronoUnit.MINUTES)));
  }

  private static void requireAligned(ZonedDateTime endpoint, long granularityMinutes) {
    if (endpoint.getSecond() != 0
        || endpoint.getNano() != 0
        || endpoint.toLocalTime().toSecondOfDay() / 60 % granularityMinutes != 0) {
      throw new BookingPolicyException(Reason.GRANULARITY);
    }
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
      BookingConfiguration configuration, Instant start, Instant end, ZoneId zone) {
    BookingSchedulingSettings settings = BookingSchedulingSettings.from(configuration);
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
      if (openingEnd.isAfter(cursor)) {
        cursor = openingEnd;
      }
      if (!cursor.isBefore(end)) {
        return;
      }
    }
    throw new BookingPolicyException(Reason.OPENING_HOURS);
  }

  private static Instant openingEnd(String end, LocalDate date, ZoneId zone) {
    if (BookingSchedulingSettings.DEFAULT_OPENING_END.equals(end)) {
      return date.plusDays(1).atStartOfDay(zone).toInstant();
    }
    return date.atTime(LocalTime.parse(end)).atZone(zone).toInstant();
  }
}
