package com.researchspace.model.booking;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.regex.Pattern;

/** One complete set of account-facing Booking display settings. */
public record BookingDisplaySettings(
    String availabilityWindowStart,
    String availabilityWindowEnd,
    BookingTimezoneMode timezoneMode,
    String customTimezone) {

  public static final String DEFAULT_AVAILABILITY_WINDOW_START = "08:00";
  public static final String DEFAULT_AVAILABILITY_WINDOW_END = "18:00";
  public static final BookingTimezoneMode DEFAULT_TIMEZONE_MODE = BookingTimezoneMode.BROWSER;

  private static final Pattern START_TIME = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d");
  private static final Pattern END_TIME = Pattern.compile("(?:(?:[01]\\d|2[0-3]):[0-5]\\d|24:00)");

  public BookingDisplaySettings {
    if (timezoneMode != BookingTimezoneMode.CUSTOM) {
      customTimezone = null;
    }
  }

  /** Nullable patch used by the global Booking settings endpoint. */
  public record Patch(
      String availabilityWindowStart,
      String availabilityWindowEnd,
      BookingTimezoneMode timezoneMode,
      String customTimezone) {

    public static Patch empty() {
      return new Patch(null, null, null, null);
    }

    /** Returns one complete value using {@code current} for omitted fields. */
    public BookingDisplaySettings merge(BookingDisplaySettings current) {
      BookingTimezoneMode mergedMode = timezoneMode == null ? current.timezoneMode() : timezoneMode;
      String mergedCustomTimezone =
          customTimezone == null ? current.customTimezone() : customTimezone;
      return new BookingDisplaySettings(
          availabilityWindowStart == null
              ? current.availabilityWindowStart()
              : availabilityWindowStart,
          availabilityWindowEnd == null ? current.availabilityWindowEnd() : availabilityWindowEnd,
          mergedMode,
          mergedCustomTimezone);
    }
  }

  public static BookingDisplaySettings defaults() {
    return new BookingDisplaySettings(
        DEFAULT_AVAILABILITY_WINDOW_START,
        DEFAULT_AVAILABILITY_WINDOW_END,
        DEFAULT_TIMEZONE_MODE,
        null);
  }

  public static BookingDisplaySettings from(BookingConfigurationDefaults defaults) {
    return new BookingDisplaySettings(
        defaults.getAvailabilityWindowStart(),
        defaults.getAvailabilityWindowEnd(),
        defaults.getTimezoneMode(),
        defaults.getCustomTimezone());
  }

  public void applyTo(BookingConfigurationDefaults defaults) {
    defaults.setAvailabilityWindowStart(availabilityWindowStart);
    defaults.setAvailabilityWindowEnd(availabilityWindowEnd);
    defaults.setTimezoneMode(timezoneMode);
    defaults.setCustomTimezone(customTimezone);
  }

  public boolean hasValidAvailabilityWindow() {
    if (!isCanonicalStart(availabilityWindowStart) || !isCanonicalEnd(availabilityWindowEnd)) {
      return false;
    }
    return minuteOfDay(availabilityWindowStart) < minuteOfDay(availabilityWindowEnd);
  }

  public boolean hasValidTimezoneChoice() {
    if (timezoneMode == null) {
      return false;
    }
    if (timezoneMode != BookingTimezoneMode.CUSTOM) {
      return customTimezone == null;
    }
    if (customTimezone == null || customTimezone.isBlank()) {
      return false;
    }
    try {
      ZoneId.of(customTimezone);
      return true;
    } catch (DateTimeException ex) {
      return false;
    }
  }

  public static boolean isCanonicalStart(String value) {
    return value != null && START_TIME.matcher(value).matches();
  }

  public static boolean isCanonicalEnd(String value) {
    return value != null && END_TIME.matcher(value).matches();
  }

  public static int minuteOfDay(String value) {
    Objects.requireNonNull(value, "Display time");
    if ("24:00".equals(value)) {
      return 24 * 60;
    }
    return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3, 5));
  }
}
