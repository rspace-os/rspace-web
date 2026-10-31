package com.researchspace.model.booking;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One complete set of scheduling rules shared by booking configuration services.
 *
 * <p>For conflicting events on the same configuration, the two buffer values define one shared
 * minimum idle gap: the larger value. The values are not added, and the gap is independent of
 * insertion order.
 */
public record BookingSchedulingSettings(
    long slotGranularityMinutes,
    List<BookingOpeningHoursEntry> openingHours,
    long bufferBeforeMinutes,
    long bufferAfterMinutes,
    long maxBookingDurationMinutes,
    boolean allowDoubleBooking) {

  public static final long DEFAULT_SLOT_GRANULARITY_MINUTES = 5;
  public static final String MIDNIGHT = "00:00";
  public static final String END_OF_DAY = "24:00";
  public static final List<BookingOpeningHoursEntry> DEFAULT_OPENING_HOURS =
      List.of(
          new BookingOpeningHoursEntry(1, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(2, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(3, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(4, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(5, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(6, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(7, MIDNIGHT, END_OF_DAY));
  public static final long DEFAULT_BUFFER_MINUTES = 0;
  public static final long DEFAULT_MAX_BOOKING_DURATION_MINUTES = 0;
  public static final boolean DEFAULT_ALLOW_DOUBLE_BOOKING = false;
  public static final long MAX_BUFFER_MINUTES = 10_080;
  public static final long MAX_BOOKING_DURATION_MINUTES = 527_040;

  /** One weekday's opening interval. */
  public record DailyHours(String start, String end) {}

  public BookingSchedulingSettings {
    openingHours =
        openingHours == null
            ? null
            : openingHours.stream()
                .sorted(
                    Comparator.nullsLast(
                        Comparator.comparingInt(BookingOpeningHoursEntry::dayOfWeek)))
                .toList();
  }

  /** Partial rules. Each null leaves its current value; openingHours replaces the complete week. */
  public record Patch(
      Long slotGranularityMinutes,
      List<BookingOpeningHoursEntry> openingHours,
      Long bufferBeforeMinutes,
      Long bufferAfterMinutes,
      Long maxBookingDurationMinutes,
      Boolean allowDoubleBooking) {

    public static Patch empty() {
      return new Patch(null, null, null, null, null, null);
    }

    /** Returns one complete value using {@code current} for every omitted field. */
    public BookingSchedulingSettings merge(BookingSchedulingSettings current) {
      return new BookingSchedulingSettings(
          slotGranularityMinutes == null
              ? current.slotGranularityMinutes()
              : slotGranularityMinutes,
          openingHours == null ? current.openingHours() : openingHours,
          bufferBeforeMinutes == null ? current.bufferBeforeMinutes() : bufferBeforeMinutes,
          bufferAfterMinutes == null ? current.bufferAfterMinutes() : bufferAfterMinutes,
          maxBookingDurationMinutes == null
              ? current.maxBookingDurationMinutes()
              : maxBookingDurationMinutes,
          allowDoubleBooking == null ? current.allowDoubleBooking() : allowDoubleBooking);
    }

    public boolean isEmpty() {
      return slotGranularityMinutes == null
          && openingHours == null
          && bufferBeforeMinutes == null
          && bufferAfterMinutes == null
          && maxBookingDurationMinutes == null
          && allowDoubleBooking == null;
    }
  }

  public static BookingSchedulingSettings from(BookingConfiguration configuration) {
    return new BookingSchedulingSettings(
        configuration.getSlotGranularityMinutes(),
        configuration.getOpeningHours(),
        configuration.getBufferBeforeMinutes(),
        configuration.getBufferAfterMinutes(),
        configuration.getMaxBookingDurationMinutes(),
        configuration.isAllowDoubleBooking());
  }

  public static BookingSchedulingSettings from(BookingConfigurationDefaults defaults) {
    return new BookingSchedulingSettings(
        defaults.getSlotGranularityMinutes(),
        defaults.getOpeningHours(),
        defaults.getBufferBeforeMinutes(),
        defaults.getBufferAfterMinutes(),
        defaults.getMaxBookingDurationMinutes(),
        defaults.isAllowDoubleBooking());
  }

  public void applyTo(BookingConfiguration configuration) {
    configuration.setSlotGranularityMinutes(slotGranularityMinutes);
    configuration.setOpeningHours(openingHours);
    configuration.setBufferBeforeMinutes(bufferBeforeMinutes);
    configuration.setBufferAfterMinutes(bufferAfterMinutes);
    configuration.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
    configuration.setAllowDoubleBooking(allowDoubleBooking);
  }

  public void applyTo(BookingConfigurationDefaults defaults) {
    defaults.setSlotGranularityMinutes(slotGranularityMinutes);
    defaults.setOpeningHours(openingHours);
    defaults.setBufferBeforeMinutes(bufferBeforeMinutes);
    defaults.setBufferAfterMinutes(bufferAfterMinutes);
    defaults.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
    defaults.setAllowDoubleBooking(allowDoubleBooking);
  }

  public static boolean isGranularityValid(long minutes) {
    return minutes == 1 || minutes == 5 || minutes == 10 || minutes == 15;
  }

  /** Returns one ISO weekday's interval, or empty when that day is closed. */
  public Optional<DailyHours> effectiveHours(int dayOfWeek) {
    return openingHours.stream()
        .filter(entry -> entry.dayOfWeek() == dayOfWeek)
        .findFirst()
        .map(entry -> new DailyHours(entry.start(), entry.end()));
  }

  /** Whether a schedule contains one to seven unique ISO weekdays with valid intervals. */
  public static boolean areOpeningHoursValid(List<BookingOpeningHoursEntry> openingHours) {
    if (openingHours == null || openingHours.isEmpty() || openingHours.size() > 7) {
      return false;
    }
    Set<Integer> days = new HashSet<>();
    for (BookingOpeningHoursEntry entry : openingHours) {
      if (entry == null
          || entry.dayOfWeek() < 1
          || entry.dayOfWeek() > 7
          || !days.add(entry.dayOfWeek())
          || !isOpeningIntervalValid(entry.start(), entry.end())) {
        return false;
      }
    }
    return true;
  }

  /** Canonical HH:mm interval with an exclusive end; 24:00 is the next local midnight. */
  public static boolean isOpeningIntervalValid(String start, String end) {
    if (!isCanonicalWallTime(start) || end == null) {
      return false;
    }
    if (END_OF_DAY.equals(end)) {
      return true;
    }
    if (!isCanonicalWallTime(end)) {
      return false;
    }
    return LocalTime.parse(start).isBefore(LocalTime.parse(end));
  }

  public static boolean isBufferValid(long minutes) {
    return minutes >= 0 && minutes <= MAX_BUFFER_MINUTES;
  }

  public static boolean isMaximumDurationValid(long minutes, long granularityMinutes) {
    return minutes == DEFAULT_MAX_BOOKING_DURATION_MINUTES
        || (granularityMinutes > 0
            && minutes >= granularityMinutes
            && minutes <= MAX_BOOKING_DURATION_MINUTES
            && minutes % granularityMinutes == 0);
  }

  private static boolean isCanonicalWallTime(String value) {
    if (value == null || value.length() != 5) {
      return false;
    }
    return value.matches(BookingOpeningHoursCodec.WALL_TIME_PATTERN);
  }
}
