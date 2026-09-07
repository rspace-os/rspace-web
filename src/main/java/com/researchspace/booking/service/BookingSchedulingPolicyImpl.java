package com.researchspace.booking.service;

import com.researchspace.booking.service.BookingPolicyException.Reason;
import com.researchspace.model.booking.BookingConfiguration;
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
      throw new BookingPolicyException(Reason.MAXIMUM_DURATION);
    }
  }

  private static void requireOpeningCoverage(
      BookingConfiguration configuration, Instant start, Instant end, ZoneId zone) {
    if ("00:00".equals(configuration.getOpeningStart())
        && "24:00".equals(configuration.getOpeningEnd())) {
      return;
    }
    LocalDate firstDate = start.atZone(zone).toLocalDate();
    LocalDate lastDate = end.minusNanos(1).atZone(zone).toLocalDate();
    Instant cursor = start;
    for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
      Instant openingStart =
          date.atTime(LocalTime.parse(configuration.getOpeningStart())).atZone(zone).toInstant();
      Instant openingEnd = openingEnd(configuration, date, zone);
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

  private static Instant openingEnd(
      BookingConfiguration configuration, LocalDate date, ZoneId zone) {
    if ("24:00".equals(configuration.getOpeningEnd())) {
      return date.plusDays(1).atStartOfDay(zone).toInstant();
    }
    return date.atTime(LocalTime.parse(configuration.getOpeningEnd())).atZone(zone).toInstant();
  }
}
