package com.researchspace.booking.service;

import com.researchspace.booking.dao.TimeSlotBookingDao.EventInterval;
import com.researchspace.booking.service.BookingCatalogueManager.Availability;
import com.researchspace.booking.service.BookingCatalogueManager.AvailabilityWindow;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Classifies one bookable item at the instant of an availability window, as the All bookable items
 * quick filters describe it.
 *
 * <p>Free time is the window's part inside the item's opening hours, less every counted confirmed
 * event widened by the item's buffers. Opening hours are resolved per date in the item's scheduling
 * time zone through {@link BookingSchedulingSettings#effectiveHours(int)}, with the wall-clock
 * rules of {@link BookingSchedulingPolicyImpl}. Maintenance always counts as busy; bookings do
 * unless the item allows double booking.
 */
final class BookingCurrentAvailability {

  private record Interval(Instant start, Instant end) {}

  private BookingCurrentAvailability() {}

  /**
   * Available now when {@code now} lies in the window and is free; free later today when some free
   * time follows {@code now} in the window (for a window that has not started yet, any free time);
   * otherwise neither.
   */
  static Optional<Availability> classify(
      BookingConfiguration configuration, List<EventInterval> events, AvailabilityWindow window) {
    Instant start = window.start();
    Instant end = window.end();
    Instant now = window.now();
    if (!start.isBefore(end) || !now.isBefore(end)) {
      return Optional.empty();
    }
    List<Interval> free =
        subtract(
            openingIntervals(configuration, start, end), busy(configuration, events, start, end));
    if (!now.isBefore(start)
        && free.stream()
            .anyMatch(interval -> !interval.start().isAfter(now) && now.isBefore(interval.end()))) {
      return Optional.of(Availability.AVAILABLE_NOW);
    }
    return free.stream().anyMatch(interval -> interval.end().isAfter(now))
        ? Optional.of(Availability.FREE_LATER_TODAY)
        : Optional.empty();
  }

  /** Opening intervals inside {@code [start, end)}, sorted, with touching intervals merged. */
  private static List<Interval> openingIntervals(
      BookingConfiguration configuration, Instant start, Instant end) {
    ZoneId zone = ZoneId.of(configuration.getTimeZone());
    BookingSchedulingSettings settings = BookingSchedulingSettings.from(configuration);
    List<Interval> merged = new ArrayList<>();
    LocalDate lastDate = end.minusNanos(1).atZone(zone).toLocalDate();
    for (LocalDate date = start.atZone(zone).toLocalDate();
        !date.isAfter(lastDate);
        date = date.plusDays(1)) {
      Optional<BookingSchedulingSettings.DailyHours> hours =
          settings.effectiveHours(date.getDayOfWeek().getValue());
      if (hours.isEmpty()) {
        continue;
      }
      Instant opening = later(boundary(date, hours.get().start(), zone), start);
      Instant closing = earlier(boundary(date, hours.get().end(), zone), end);
      if (!opening.isBefore(closing)) {
        continue;
      }
      int last = merged.size() - 1;
      if (last >= 0 && !opening.isAfter(merged.get(last).end())) {
        Interval previous = merged.get(last);
        merged.set(last, new Interval(previous.start(), later(previous.end(), closing)));
      } else {
        merged.add(new Interval(opening, closing));
      }
    }
    return merged;
  }

  /** A scheduling-zone wall-clock boundary; {@code 24:00} is the next date's start of day. */
  private static Instant boundary(LocalDate date, String time, ZoneId zone) {
    return BookingSchedulingSettings.DEFAULT_OPENING_END.equals(time)
        ? date.plusDays(1).atStartOfDay(zone).toInstant()
        : date.atTime(LocalTime.parse(time)).atZone(zone).toInstant();
  }

  /** Counted events widened by the item's buffers and clipped to {@code [start, end)}. */
  private static List<Interval> busy(
      BookingConfiguration configuration, List<EventInterval> events, Instant start, Instant end) {
    return events.stream()
        .filter(
            event ->
                event.kind() == BookingEventKind.MAINTENANCE
                    || !configuration.isAllowDoubleBooking())
        .map(
            event ->
                new Interval(
                    later(
                        event
                            .start()
                            .toInstant()
                            .minus(configuration.getBufferBeforeMinutes(), ChronoUnit.MINUTES),
                        start),
                    earlier(
                        event
                            .end()
                            .toInstant()
                            .plus(configuration.getBufferAfterMinutes(), ChronoUnit.MINUTES),
                        end)))
        .filter(interval -> interval.start().isBefore(interval.end()))
        .sorted(Comparator.comparing(Interval::start))
        .toList();
  }

  /** The parts of the sorted, disjoint {@code openings} that no {@code busy} interval covers. */
  private static List<Interval> subtract(List<Interval> openings, List<Interval> busy) {
    List<Interval> free = new ArrayList<>();
    for (Interval opening : openings) {
      Instant cursor = opening.start();
      for (Interval taken : busy) {
        if (!cursor.isBefore(opening.end())) {
          break;
        }
        if (!taken.end().isAfter(cursor) || !taken.start().isBefore(opening.end())) {
          continue;
        }
        if (taken.start().isAfter(cursor)) {
          free.add(new Interval(cursor, taken.start()));
        }
        cursor = later(cursor, taken.end());
      }
      if (cursor.isBefore(opening.end())) {
        free.add(new Interval(cursor, opening.end()));
      }
    }
    return free;
  }

  private static Instant later(Instant first, Instant second) {
    return first.isAfter(second) ? first : second;
  }

  private static Instant earlier(Instant first, Instant second) {
    return first.isBefore(second) ? first : second;
  }
}
