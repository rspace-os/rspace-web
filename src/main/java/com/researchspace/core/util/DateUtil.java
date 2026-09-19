package com.researchspace.core.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.TimeZone;
import org.apache.commons.lang3.Validate;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/** Date Utility Class used to convert between Strings and Dates/Timestamps */
public class DateUtil {

  /**
   * This method generate a string representation of a date based on ISO 8061 format
   *
   * @param aDate
   * @param timezone, optional, defaults to UTC if null.
   * @return an ISO 8061 representation of aDate
   */
  public static String convertDateToISOFormat(Date aDate, TimeZone timezone) {
    Validate.notNull(aDate, "date cannot be null");
    return convertDateToISOFormat(aDate.getTime(), timezone);
  }

  /**
   * This method generate a string representation of a date based on ISO 8061 format
   *
   * @param timeStampMillis a long timestamp
   * @param timezone, optional, defaults to UTC if null.
   * @return an ISO 8061 representation of timeStampMillis
   */
  public static String convertDateToISOFormat(Long timeStampMillis, TimeZone timezone) {
    Validate.notNull(timeStampMillis, "timestamp cannot be null");
    timezone = (timezone == null) ? DateTimeZone.UTC.toTimeZone() : timezone;
    return new DateTime(timeStampMillis, DateTimeZone.forTimeZone(timezone)).toString();
  }

  /**
   * This method generate a string representation of a date based on ISO 8061 format
   *
   * @param iso8601DateTime a string ISO-8601
   * @return millis since epoch.
   * @throws DateTimeParseException if not valid ISO8601 date time
   */
  public static Long convertISO8601ToMillis(String iso8601DateTime) {
    DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    return toMilli(iso8601DateTime, timeFormatter);
  }

  private static Long toMilli(String iso8601Date, DateTimeFormatter timeFormatter) {
    TemporalAccessor accessor = timeFormatter.parse(iso8601Date);
    return Instant.from(accessor).toEpochMilli();
  }

  /**
   * This method generate a millis representation of a date based on ISO 8061 date format with no
   * time, e.g. 2001-11-30, setting the seconds to be the start-of-day.
   *
   * @param iso8601Date a string ISO-8601
   * @return millis since epoch.
   * @throws DateTimeParseException if not valid ISO8601 date
   */
  public static Long convertISODateToMillis(String iso8601Date) {
    DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE;
    LocalDateTime d = LocalDate.parse(iso8601Date, timeFormatter).atStartOfDay();
    OffsetDateTime offsetDate = OffsetDateTime.of(d, ZoneOffset.ofHours(0));
    return offsetDate.toEpochSecond() * 1000;
  }

  @SuppressWarnings("deprecation")
  public static int getRealGMT(int gmt) {
    Date d = new Date();
    return gmt - ((d.getTimezoneOffset() / 60) * (-1));
  }

  /**
   * Converts a Java.time LocalDate to a java.util.Date,set to midnight at start of day.
   *
   * @param localDate
   * @return A java.util.Date, in UTC timezone, set to midnight at start of day.
   */
  public static Date localDateToDateUTC(LocalDate localDate) {
    return Date.from(localDate.atStartOfDay(ZoneId.of("Z")).toInstant());
  }
}
