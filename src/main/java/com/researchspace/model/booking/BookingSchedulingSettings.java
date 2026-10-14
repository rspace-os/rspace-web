package com.researchspace.model.booking;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One complete set of scheduling rules shared by booking configuration services.
 *
 * <p>For conflicting events on the same configuration, the two buffer values define one shared
 * minimum idle gap: the larger value. The values are not added, and the gap is independent of
 * insertion order.
 *
 * <p>{@code openingHours} contains one independent interval for each open ISO weekday. Omitted
 * weekdays are closed. Resolve a day's hours only through {@link #effectiveHours(int)}.
 *
 * <p>{@code slotGranularityMinutes} is the increment in minutes. For {@link
 * BookingIncrementUnit#DAYS} it is the day count times 1440, a nominal value that is never compared
 * with elapsed time; bookings are checked against {@link #incrementRule()}.
 */
public record BookingSchedulingSettings(
    long slotGranularityMinutes,
    BookingIncrementUnit incrementUnit,
    boolean alignToIncrement,
    List<BookingOpeningHoursEntry> openingHours,
    long bufferBeforeMinutes,
    long bufferAfterMinutes,
    long maxBookingDurationMinutes,
    boolean allowDoubleBooking) {

  public static final long DEFAULT_SLOT_GRANULARITY_MINUTES = 15;
  public static final BookingIncrementUnit DEFAULT_INCREMENT_UNIT = BookingIncrementUnit.MINUTES;
  public static final boolean DEFAULT_ALIGN_TO_INCREMENT = true;
  public static final String MIDNIGHT = "00:00";
  public static final String END_OF_DAY = "24:00";
  public static final long DEFAULT_BUFFER_MINUTES = 0;
  public static final long DEFAULT_MAX_BOOKING_DURATION_MINUTES = 0;
  public static final boolean DEFAULT_ALLOW_DOUBLE_BOOKING = false;
  public static final List<BookingOpeningHoursEntry> DEFAULT_OPENING_HOURS =
      List.of(
          new BookingOpeningHoursEntry(1, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(2, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(3, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(4, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(5, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(6, MIDNIGHT, END_OF_DAY),
          new BookingOpeningHoursEntry(7, MIDNIGHT, END_OF_DAY));
  public static final long MAX_BUFFER_MINUTES = 10_080;
  public static final long MAX_BOOKING_DURATION_MINUTES = 527_040;
  public static final long MINUTES_PER_DAY = 1440;

  private static final long MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

  /** One weekday's opening interval. */
  public record DailyHours(String start, String end) {}

  private static final DailyHours FULL_DAY = new DailyHours(MIDNIGHT, END_OF_DAY);

  public BookingSchedulingSettings {
    openingHours = sorted(openingHours);
  }

  /** How a booking's start and end are checked against the increment. */
  public sealed interface IncrementRule permits ElapsedIncrement, DayIncrement {}

  /**
   * Elapsed time: the booking lasts a positive multiple of {@code minutes}. When {@code aligned},
   * its start's local minute of day is also a multiple of {@code minutes}.
   */
  public record ElapsedIncrement(long minutes, boolean aligned) implements IncrementRule {}

  /**
   * Calendar days: the booking ends at its start's local time, a positive multiple of {@code days}
   * local dates later.
   */
  public record DayIncrement(long days) implements IncrementRule {}

  /**
   * Partial scheduling settings where each null field keeps its current value. A supplied {@code
   * openingHours} replaces the whole weekly schedule.
   */
  public record Patch(
      Long slotGranularityMinutes,
      BookingIncrementUnit incrementUnit,
      Boolean alignToIncrement,
      List<BookingOpeningHoursEntry> openingHours,
      Long bufferBeforeMinutes,
      Long bufferAfterMinutes,
      Long maxBookingDurationMinutes,
      Boolean allowDoubleBooking) {

    /**
     * A patch that leaves the increment unit, the alignment choice and weekly schedule unchanged.
     */
    public Patch(
        Long slotGranularityMinutes,
        List<BookingOpeningHoursEntry> openingHours,
        Long bufferBeforeMinutes,
        Long bufferAfterMinutes,
        Long maxBookingDurationMinutes,
        Boolean allowDoubleBooking) {
      this(
          slotGranularityMinutes,
          null,
          null,
          openingHours,
          bufferBeforeMinutes,
          bufferAfterMinutes,
          maxBookingDurationMinutes,
          allowDoubleBooking);
    }

    public static Patch empty() {
      return new Patch(null, null, null, null, null, null, null, null);
    }

    /** Returns one complete value using {@code current} for every omitted field. */
    public BookingSchedulingSettings merge(BookingSchedulingSettings current) {
      return new BookingSchedulingSettings(
          slotGranularityMinutes == null
              ? current.slotGranularityMinutes()
              : slotGranularityMinutes,
          incrementUnit == null ? current.incrementUnit() : incrementUnit,
          alignToIncrement == null ? current.alignToIncrement() : alignToIncrement,
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
          && incrementUnit == null
          && alignToIncrement == null
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
        configuration.getIncrementUnit(),
        configuration.isAlignToIncrement(),
        configuration.getOpeningHours(),
        configuration.getBufferBeforeMinutes(),
        configuration.getBufferAfterMinutes(),
        configuration.getMaxBookingDurationMinutes(),
        configuration.isAllowDoubleBooking());
  }

  public static BookingSchedulingSettings from(BookingConfigurationDefaults defaults) {
    return new BookingSchedulingSettings(
        defaults.getSlotGranularityMinutes(),
        defaults.getIncrementUnit(),
        defaults.isAlignToIncrement(),
        defaults.getOpeningHours(),
        defaults.getBufferBeforeMinutes(),
        defaults.getBufferAfterMinutes(),
        defaults.getMaxBookingDurationMinutes(),
        defaults.isAllowDoubleBooking());
  }

  public void applyTo(BookingConfiguration configuration) {
    configuration.setSlotGranularityMinutes(slotGranularityMinutes);
    configuration.setIncrementUnit(incrementUnit);
    configuration.setAlignToIncrement(alignToIncrement);
    configuration.setOpeningHours(openingHours);
    configuration.setBufferBeforeMinutes(bufferBeforeMinutes);
    configuration.setBufferAfterMinutes(bufferAfterMinutes);
    configuration.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
    configuration.setAllowDoubleBooking(allowDoubleBooking);
  }

  public void applyTo(BookingConfigurationDefaults defaults) {
    defaults.setSlotGranularityMinutes(slotGranularityMinutes);
    defaults.setIncrementUnit(incrementUnit);
    defaults.setAlignToIncrement(alignToIncrement);
    defaults.setOpeningHours(openingHours);
    defaults.setBufferBeforeMinutes(bufferBeforeMinutes);
    defaults.setBufferAfterMinutes(bufferAfterMinutes);
    defaults.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
    defaults.setAllowDoubleBooking(allowDoubleBooking);
  }

  /** Returns the opening interval of one ISO weekday, or empty when that day is closed. */
  public Optional<DailyHours> effectiveHours(int dayOfWeek) {
    return openingHours.stream()
        .filter(entry -> entry.dayOfWeek() == dayOfWeek)
        .findFirst()
        .map(entry -> new DailyHours(entry.start(), entry.end()));
  }

  /** Whether a schedule has one to seven unique ISO weekdays with valid intervals. */
  public static boolean areOpeningHoursValid(List<BookingOpeningHoursEntry> openingHours) {
    if (openingHours == null || openingHours.isEmpty() || openingHours.size() > 7) {
      return false;
    }
    Set<Integer> days = new java.util.HashSet<>();
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

  private static List<BookingOpeningHoursEntry> sorted(
      List<BookingOpeningHoursEntry> openingHours) {
    return openingHours == null
        ? null
        : openingHours.stream()
            .sorted(
                Comparator.nullsLast(Comparator.comparingInt(BookingOpeningHoursEntry::dayOfWeek)))
            .toList();
  }

  /**
   * Whether booking starts must sit on the increment's grid, counted from local midnight. The
   * stored {@code alignToIncrement} choice applies only to an elapsed increment shorter than one
   * day; it is ignored, never rewritten, for longer increments and for days.
   */
  public boolean effectiveAlignment() {
    return alignToIncrement
        && incrementUnit != BookingIncrementUnit.DAYS
        && slotGranularityMinutes < MINUTES_PER_DAY;
  }

  /** The rule bookings are checked against; requires a valid increment. */
  public IncrementRule incrementRule() {
    return incrementUnit == BookingIncrementUnit.DAYS
        ? new DayIncrement(slotGranularityMinutes / MINUTES_PER_DAY)
        : new ElapsedIncrement(slotGranularityMinutes, effectiveAlignment());
  }

  /**
   * Whether the increment fits the opening hours. Days need every ISO weekday open from 00:00 to
   * 24:00. An aligned elapsed increment needs a start on its midnight grid inside a continuous
   * opening period with room for one increment; an unaligned one needs a continuous opening period
   * at least one increment long.
   */
  public boolean incrementFitsOpeningHours() {
    if (incrementUnit == BookingIncrementUnit.DAYS) {
      return isOpenAllDayEveryDay();
    }
    return effectiveAlignment()
        ? alignedIncrementFitsOpening()
        : slotGranularityMinutes <= longestOpeningMinutes();
  }

  /** Whether every ISO weekday is effectively open from 00:00 to 24:00. */
  public boolean isOpenAllDayEveryDay() {
    for (int day = 1; day <= 7; day++) {
      if (!effectiveHours(day).filter(FULL_DAY::equals).isPresent()) {
        return false;
      }
    }
    return true;
  }

  /** Longest continuous weekly opening, merging touching days across the week boundary. */
  public long longestOpeningMinutes() {
    long longest = 0;
    for (long[] period : continuousOpenings()) {
      longest = Math.max(longest, period[1] - period[0]);
    }
    return longest >= MINUTES_PER_WEEK ? MAX_BOOKING_DURATION_MINUTES : longest;
  }

  /**
   * Whether some continuous opening period contains {@code [start, start + increment]} for a start
   * whose minute of day is a multiple of the increment. The grid restarts at each local midnight,
   * so each covered day is tried from its first grid minute inside the period.
   */
  private boolean alignedIncrementFitsOpening() {
    long increment = slotGranularityMinutes;
    for (long[] period : continuousOpenings()) {
      for (long dayStart = period[0] / MINUTES_PER_DAY * MINUTES_PER_DAY;
          dayStart < period[1];
          dayStart += MINUTES_PER_DAY) {
        long earliest = Math.max(period[0], dayStart) - dayStart;
        long start = dayStart + (earliest + increment - 1) / increment * increment;
        if (start < dayStart + MINUTES_PER_DAY && start + increment <= period[1]) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Continuous opening periods as {@code [start, end)} minutes from the first Monday's midnight,
   * over two unrolled weeks so a period across the week boundary appears whole.
   */
  private List<long[]> continuousOpenings() {
    List<long[]> periods = new ArrayList<>();
    for (int day = 0; day < 14; day++) {
      Optional<DailyHours> hours = effectiveHours(day % 7 + 1);
      if (hours.isEmpty()) {
        continue;
      }
      long opening = day * MINUTES_PER_DAY + minuteOfDay(hours.get().start());
      long closing =
          day * MINUTES_PER_DAY
              + (END_OF_DAY.equals(hours.get().end())
                  ? MINUTES_PER_DAY
                  : minuteOfDay(hours.get().end()));
      long[] previous = periods.isEmpty() ? null : periods.get(periods.size() - 1);
      if (previous != null && previous[1] == opening) {
        previous[1] = closing;
      } else {
        periods.add(new long[] {opening, closing});
      }
    }
    return periods;
  }

  private static long minuteOfDay(String wallTime) {
    return LocalTime.parse(wallTime).toSecondOfDay() / 60;
  }

  /**
   * Whether {@code minutes} is a valid increment in {@code unit}: a whole number of minutes from 1
   * to the absolute booking limit and, for days, a whole number of days.
   */
  public static boolean isGranularityValid(long minutes, BookingIncrementUnit unit) {
    return unit != null
        && minutes >= 1
        && minutes <= MAX_BOOKING_DURATION_MINUTES
        && (unit != BookingIncrementUnit.DAYS || minutes % MINUTES_PER_DAY == 0);
  }

  /**
   * Whether {@code start}-{@code end} is one opening interval: canonical {@code HH:mm} times with
   * start before end. An end of {@code 24:00} closes at the next local midnight, so it is valid
   * after any start, for example {@code 18:00}-{@code 24:00} as well as the full day.
   */
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

  /**
   * Whether {@code minutes} is a valid elapsed maximum for the increment: 0 for no item limit, or
   * from one increment up to the absolute booking limit. Elapsed increments also need a multiple of
   * the increment. Days need only the nominal {@code days * 1440}, because a calendar day can be
   * shorter or longer than 1440 elapsed minutes.
   */
  public static boolean isMaximumDurationValid(
      long minutes, long granularityMinutes, BookingIncrementUnit unit) {
    if (minutes == DEFAULT_MAX_BOOKING_DURATION_MINUTES) {
      return true;
    }
    if (unit == null
        || granularityMinutes <= 0
        || minutes < granularityMinutes
        || minutes > MAX_BOOKING_DURATION_MINUTES) {
      return false;
    }
    return unit == BookingIncrementUnit.DAYS || minutes % granularityMinutes == 0;
  }

  private static boolean isCanonicalWallTime(String value) {
    if (value == null || value.length() != 5) {
      return false;
    }
    return value.matches(BookingOpeningHoursCodec.WALL_TIME_PATTERN);
  }
}
