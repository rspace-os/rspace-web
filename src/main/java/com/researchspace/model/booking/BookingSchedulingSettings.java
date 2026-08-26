package com.researchspace.model.booking;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** One complete set of scheduling rules shared by booking configuration services. */
public record BookingSchedulingSettings(
    long slotGranularityMinutes,
    String openingStart,
    String openingEnd,
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
  public static final long MAX_BUFFER_MINUTES = 10_080;
  public static final long MAX_BOOKING_DURATION_MINUTES = 527_040;

  private static final DateTimeFormatter WALL_TIME =
      DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

  public record Patch(
      Long slotGranularityMinutes,
      String openingStart,
      String openingEnd,
      Long bufferBeforeMinutes,
      Long bufferAfterMinutes,
      Long maxBookingDurationMinutes,
      Boolean allowDoubleBooking) {

    public static Patch empty() {
      return new Patch(null, null, null, null, null, null, null);
    }

    /** Returns one complete value using {@code current} for every omitted field. */
    public BookingSchedulingSettings merge(BookingSchedulingSettings current) {
      return new BookingSchedulingSettings(
          slotGranularityMinutes == null
              ? current.slotGranularityMinutes()
              : slotGranularityMinutes,
          openingStart == null ? current.openingStart() : openingStart,
          openingEnd == null ? current.openingEnd() : openingEnd,
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
        defaults.getBufferBeforeMinutes(),
        defaults.getBufferAfterMinutes(),
        defaults.getMaxBookingDurationMinutes(),
        defaults.isAllowDoubleBooking());
  }

  public void applyTo(BookingConfiguration configuration) {
    configuration.setSlotGranularityMinutes(slotGranularityMinutes);
    configuration.setOpeningStart(openingStart);
    configuration.setOpeningEnd(openingEnd);
    configuration.setBufferBeforeMinutes(bufferBeforeMinutes);
    configuration.setBufferAfterMinutes(bufferAfterMinutes);
    configuration.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
    configuration.setAllowDoubleBooking(allowDoubleBooking);
  }

  public void applyTo(BookingConfigurationDefaults defaults) {
    defaults.setSlotGranularityMinutes(slotGranularityMinutes);
    defaults.setOpeningStart(openingStart);
    defaults.setOpeningEnd(openingEnd);
    defaults.setBufferBeforeMinutes(bufferBeforeMinutes);
    defaults.setBufferAfterMinutes(bufferAfterMinutes);
    defaults.setMaxBookingDurationMinutes(maxBookingDurationMinutes);
    defaults.setAllowDoubleBooking(allowDoubleBooking);
  }

  public static boolean isGranularityValid(long minutes) {
    return minutes == 1 || minutes == 5 || minutes == 15;
  }

  public static boolean areOpeningHoursValid(String start, String end) {
    if (!isCanonicalWallTime(start) || end == null) {
      return false;
    }
    if (DEFAULT_OPENING_END.equals(end)) {
      return DEFAULT_OPENING_START.equals(start);
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
