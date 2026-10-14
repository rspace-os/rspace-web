package com.researchspace.booking.service;

import com.researchspace.booking.service.BookingDisplayPreferencesManager.ResolvedBookingDisplayPreferences;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.BookingNotificationData;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/** Formats booking notification intervals for the recipient's display timezone. */
@Service
public class BookingNotificationMessageFormatter {

  private static final String CREATED_MESSAGE_KEY = "bookingNotifications.created";
  private static final String CANCELLED_MESSAGE_KEY = "bookingNotifications.cancelled";
  private static final Pattern FINAL_ISO_INSTANT =
      Pattern.compile("(?:\\d{4}|[+-]\\d{6})-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z$");

  private final MessageSource messageSource;

  public BookingNotificationMessageFormatter(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /**
   * Resolves the viewer's configured zone, using the institution zone when browser data is absent.
   */
  public static ZoneId zoneFor(
      ResolvedBookingDisplayPreferences preferences, ZoneId browserZone, ZoneId fallbackZone) {
    if (preferences == null) {
      return browserZone == null ? fallbackZone : browserZone;
    }
    return switch (preferences.timezoneMode()) {
      case CUSTOM -> validZone(preferences.customTimezone()).orElse(fallbackZone);
      case INSTITUTION -> validZone(preferences.institutionTimezone()).orElse(fallbackZone);
      case BROWSER ->
          browserZone != null
              ? browserZone
              : validZone(preferences.institutionTimezone()).orElse(fallbackZone);
    };
  }

  /** Formats structured booking data as an HTML-safe message for the given recipient zone. */
  public String format(
      NotificationType type, BookingNotificationData data, ZoneId zone, Locale locale) {
    if (!isBookingNotification(type)) {
      throw new IllegalArgumentException("Unsupported booking notification type: " + type);
    }
    return messageSource.getMessage(
        messageKey(type),
        new Object[] {
          StringEscapeUtils.escapeHtml4(data.getBookingId()),
          StringEscapeUtils.escapeHtml4(data.getInstrumentName()),
          StringEscapeUtils.escapeHtml4(data.getInstrumentGlobalIdentifier()),
          formatInstant(data.getStartTime(), zone, locale),
          formatInstant(data.getEndTime(), zone, locale)
        },
        locale);
  }

  /**
   * Formats an older stored booking message if its final interval is still in canonical ISO form.
   */
  public String formatLegacy(NotificationType type, String message, ZoneId zone, Locale locale) {
    if (!isBookingNotification(type) || message == null) {
      return message;
    }
    int period = message.length() - 1;
    int to = message.lastIndexOf(" to ", period);
    int from = to < 0 ? -1 : message.lastIndexOf(" from ", to);
    if (period < 0 || message.charAt(period) != '.' || to < 0 || from < 0) {
      return message;
    }
    int startIndex = from + " from ".length();
    String start = message.substring(startIndex, to);
    String end = message.substring(to + " to ".length(), period);
    if (!isCanonicalInstant(start) || !isCanonicalInstant(end)) {
      return message;
    }
    return message.substring(0, startIndex)
        + formatInstant(start, zone, locale)
        + " to "
        + formatInstant(end, zone, locale)
        + message.substring(period);
  }

  public static boolean isBookingNotification(NotificationType type) {
    return NotificationType.NOTIFICATION_BOOKING_CREATED.equals(type)
        || NotificationType.NOTIFICATION_BOOKING_CANCELLED.equals(type);
  }

  private static String messageKey(NotificationType type) {
    return NotificationType.NOTIFICATION_BOOKING_CREATED.equals(type)
        ? CREATED_MESSAGE_KEY
        : CANCELLED_MESSAGE_KEY;
  }

  private static String formatInstant(String value, ZoneId zone, Locale locale) {
    Instant instant = Instant.parse(value);
    String localDateTime =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .format(instant);
    String offset = zone.getRules().getOffset(instant).getId();
    return localDateTime + " (" + zone.getId() + ", UTC" + ("Z".equals(offset) ? "" : offset) + ")";
  }

  private static boolean isCanonicalInstant(String value) {
    if (!FINAL_ISO_INSTANT.matcher(value).matches()) {
      return false;
    }
    try {
      return DateTimeFormatter.ISO_INSTANT.format(Instant.parse(value)).equals(value);
    } catch (DateTimeException ex) {
      return false;
    }
  }

  private static Optional<ZoneId> validZone(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(ZoneId.of(value));
    } catch (DateTimeException ex) {
      return Optional.empty();
    }
  }
}
