package com.researchspace.model.booking;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One complete set of scheduling rules shared by booking configuration services.
 *
 * <p>{@code openingStart}/{@code openingEnd} is the shared daily interval. It applies to each ISO
 * weekday in {@code openDays} unless that weekday has its own entry in {@code openingExceptions}.
 * Resolve a day's hours only through {@link #effectiveHours(int)}.
 */
public record BookingSchedulingSettings(
    long slotGranularityMinutes,
    String openingStart,
    String openingEnd,
    List<Integer> openDays,
    List<BookingOpeningException> openingExceptions,
    long bufferBeforeMinutes,
    long bufferAfterMinutes,
    long maxBookingDurationMinutes,
    boolean allowDoubleBooking) {

  public static final long DEFAULT_SLOT_GRANULARITY_MINUTES = 5;
  public static final String DEFAULT_OPENING_START = "00:00";
  public static final String DEFAULT_OPENING_END = "24:00";
  public static final long DEFAULT_BUFFER_MINUTES = 0;
  public static final long DEFAULT_MAX_BOOKING_DURATION_MINUTES = 0;
  public static final boolean DEFAULT_ALLOW_DOUBLE_BOOKING = false;
  public static final List<Integer> DEFAULT_OPEN_DAYS = List.of(1, 2, 3, 4, 5, 6, 7);
  public static final List<BookingOpeningException> DEFAULT_OPENING_EXCEPTIONS = List.of();
  public static final long MAX_BUFFER_MINUTES = 10_080;
  public static final long MAX_BOOKING_DURATION_MINUTES = 527_040;

  private static final DateTimeFormatter WALL_TIME =
      DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

  /** One weekday's opening interval, with the same format rules as the shared interval. */
  public record DailyHours(String start, String end) {}

  /**
   * Partial scheduling settings where each null field keeps its current value. A supplied {@code
   * openDays} or {@code openingExceptions} replaces that whole list.
   */
  public record Patch(
      Long slotGranularityMinutes,
      String openingStart,
      String openingEnd,
      List<Integer> openDays,
      List<BookingOpeningException> openingExceptions,
      Long bufferBeforeMinutes,
      Long bufferAfterMinutes,
      Long maxBookingDurationMinutes,
      Boolean allowDoubleBooking) {

    /** A patch that leaves the weekday selection and exceptions unchanged. */
    public Patch(
        Long slotGranularityMinutes,
        String openingStart,
        String openingEnd,
        Long bufferBeforeMinutes,
        Long bufferAfterMinutes,
        Long maxBookingDurationMinutes,
        Boolean allowDoubleBooking) {
      this(
          slotGranularityMinutes,
          openingStart,
          openingEnd,
          null,
          null,
          bufferBeforeMinutes,
          bufferAfterMinutes,
          maxBookingDurationMinutes,
          allowDoubleBooking);
    }

    public static Patch empty() {
      return new Patch(null, null, null, null, null, null, null, null, null);
    }

    /** Returns one complete value using {@code current} for every omitted field. */
    public BookingSchedulingSettings merge(BookingSchedulingSettings current) {
      return new BookingSchedulingSettings(
          slotGranularityMinutes == null
              ? current.slotGranularityMinutes()
              : slotGranularityMinutes,
          openingStart == null ? current.openingStart() : openingStart,
          openingEnd == null ? current.openingEnd() : openingEnd,
          openDays == null ? current.openDays() : openDays,
          openingExceptions == null ? current.openingExceptions() : openingExceptions,
          bufferBeforeMinutes == null ? current.bufferBeforeMinutes() : bufferBeforeMinutes,
          bufferAfterMinutes == null ? current.bufferAfterMinutes() : bufferAfterMinutes,
          maxBookingDurationMinutes == null
              ? current.maxBookingDurationMinutes()
              : maxBookingDurationMinutes,
          allowDoubleBooking == null ? current.allowDoubleBooking() : allowDoubleBooking);
    }

    public boolean isEmpty() {
      return slotGranularityMinutes == null
          && openingStart == null
          && openingEnd == null
          && openDays == null
          && openingExceptions == null
          && bufferBeforeMinutes == null
          && bufferAfterMinutes == null
          && maxBookingDurationMinutes == null
          && allowDoubleBooking == null;
    }
  }

  public static BookingSchedulingSettings from(BookingConfiguration configuration) {
    return new BookingSchedulingSettings(
        configuration.getSlotGranularityMinutes(),
        configuration.getOpeningStart(),
        configuration.getOpeningEnd(),
        configuration.getOpenDays(),
        configuration.getOpeningExceptions(),
        configuration.getBufferBeforeMinutes(),
        configuration.getBufferAfterMinutes(),
        configuration.getMaxBookingDurationMinutes(),
        configuration.isAllowDoubleBooking());
  }

  public static BookingSchedulingSettings from(BookingConfigurationDefaults defaults) {
    return new BookingSchedulingSettings(
        defaults.getSlotGranularityMinutes(),
        defaults.getOpeningStart(),
        defaults.getOpeningEnd(),
        defaults.getOpenDays(),
        defaults.getOpeningExceptions(),
        defaults.getBufferBeforeMinutes(),
        defaults.getBufferAfterMinutes(),
        defaults.getMaxBookingDurationMinutes(),
        defaults.isAllowDoubleBooking());
  }

  public void applyTo(BookingConfiguration configuration) {
    configuration.setSlotGranularityMinutes(slotGranularityMinutes);
    configuration.setOpeningStart(openingStart);
    configuration.setOpeningEnd(openingEnd);
    configuration.setOpenDays(openDays);
    configuration.setOpeningExceptions(openingExceptions);
    configuration.setBufferBeforeMinutes(bufferBeforeMinutes);
    configuration.setBufferAfterMinutes(bufferAfterMinutes);
    configuration.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
    configuration.setAllowDoubleBooking(allowDoubleBooking);
  }

  public void applyTo(BookingConfigurationDefaults defaults) {
    defaults.setSlotGranularityMinutes(slotGranularityMinutes);
    defaults.setOpeningStart(openingStart);
    defaults.setOpeningEnd(openingEnd);
    defaults.setOpenDays(openDays);
    defaults.setOpeningExceptions(openingExceptions);
    defaults.setBufferBeforeMinutes(bufferBeforeMinutes);
    defaults.setBufferAfterMinutes(bufferAfterMinutes);
    defaults.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
    defaults.setAllowDoubleBooking(allowDoubleBooking);
  }

  /**
   * Returns the opening interval of one ISO weekday: empty when the day is closed, the day's
   * exception when it has one, otherwise the shared interval.
   */
  public Optional<DailyHours> effectiveHours(int dayOfWeek) {
    if (!openDays.contains(dayOfWeek)) {
      return Optional.empty();
    }
    return Optional.of(
        openingExceptions.stream()
            .filter(exception -> exception.dayOfWeek() == dayOfWeek)
            .findFirst()
            .map(exception -> new DailyHours(exception.start(), exception.end()))
            .orElseGet(() -> new DailyHours(openingStart, openingEnd)));
  }

  /** Whether {@code days} is a nonempty set of unique ISO weekdays. */
  public static boolean areOpenDaysValid(List<Integer> days) {
    if (days == null || days.isEmpty() || days.size() > 7) {
      return false;
    }
    Set<Integer> unique = new HashSet<>();
    for (Integer day : days) {
      if (day == null || day < 1 || day > 7 || !unique.add(day)) {
        return false;
      }
    }
    return true;
  }

  /** Whether every exception has a unique ISO weekday and a valid interval. */
  public static boolean areOpeningExceptionsValid(List<BookingOpeningException> exceptions) {
    if (exceptions == null || exceptions.size() > 7) {
      return false;
    }
    Set<Integer> days = new HashSet<>();
    for (BookingOpeningException exception : exceptions) {
      if (exception == null
          || exception.dayOfWeek() < 1
          || exception.dayOfWeek() > 7
          || !days.add(exception.dayOfWeek())
          || !areOpeningHoursValid(exception.start(), exception.end())) {
        return false;
      }
    }
    return true;
  }

  /** Whether every exception belongs to an open weekday. */
  public static boolean areOpeningExceptionsOnOpenDays(
      List<BookingOpeningException> exceptions, List<Integer> openDays) {
    return exceptions != null
        && openDays != null
        && exceptions.stream()
            .allMatch(exception -> exception != null && openDays.contains(exception.dayOfWeek()));
  }

  public static boolean isGranularityValid(long minutes) {
    return minutes == 1 || minutes == 5 || minutes == 10 || minutes == 15;
  }

  /**
   * Whether {@code start}-{@code end} is one opening interval: canonical {@code HH:mm} times with
   * start before end. An end of {@code 24:00} closes at the next local midnight, so it is valid
   * after any start, for example {@code 18:00}-{@code 24:00} as well as the full day.
   */
  public static boolean areOpeningHoursValid(String start, String end) {
    if (!isCanonicalWallTime(start) || end == null) {
      return false;
    }
    if (DEFAULT_OPENING_END.equals(end)) {
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
    try {
      return LocalTime.parse(value, WALL_TIME)
          .format(DateTimeFormatter.ofPattern("HH:mm"))
          .equals(value);
    } catch (DateTimeParseException exception) {
      return false;
    }
  }
}
