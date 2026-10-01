package com.researchspace.booking.service;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.DateTimePatternGenerator;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;
import com.researchspace.booking.service.BookingDisplayPreferencesManager.ResolvedBookingDisplayPreferences;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.comms.data.BookingNotificationData;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * Formats booking notification messages for the recipient's display timezone.
 *
 * <p>Messages are HTML: user-controlled values are escaped, and the booking and its bookable item
 * are links. In-app messages use root-relative links; messages that leave RSpace, such as email,
 * use absolute links built from {@code server.urls.prefix}, like the rest of the email template.
 */
@Service
public class BookingNotificationMessageFormatter {

  private static final Logger log =
      LoggerFactory.getLogger(BookingNotificationMessageFormatter.class);

  private static final String CREATED_MESSAGE_KEY = "bookingNotifications.created";
  private static final String CANCELLED_MESSAGE_KEY = "bookingNotifications.cancelled";
  private static final String BOOKING_PATH = "/booking/calendar/bookings/";
  private static final String BOOKABLE_ITEM_PATH = "/booking/bookable-items/";
  private static final Pattern FINAL_ISO_INSTANT =
      Pattern.compile("(?:\\d{4}|[+-]\\d{6})-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z$");

  private final MessageSource messageSource;
  private final String absoluteLinkBase;

  public BookingNotificationMessageFormatter(
      MessageSource messageSource, @Value("${server.urls.prefix}") String serverUrlPrefix) {
    this.messageSource = messageSource;
    this.absoluteLinkBase = stripTrailingSlashes(serverUrlPrefix);
    // Checked once at startup rather than per email: failing inside a booking transaction would
    // block
    // the booking itself over a configuration problem.
    if (!isAbsoluteHttpUrl(absoluteLinkBase)) {
      log.warn(
          "server.urls.prefix '{}' is not an absolute http(s) URL; links in booking notification"
              + " emails will not work",
          absoluteLinkBase);
    }
  }

  /** Whether {@code value} is an absolute http or https URL with a host, as email links need. */
  static boolean isAbsoluteHttpUrl(String value) {
    try {
      URI uri = new URI(value);
      return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null;
    } catch (URISyntaxException ex) {
      return false;
    }
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

  /**
   * Resolves the browser's regional format from the primary locale of an {@code Accept-Language}
   * header, or null when the header is absent, malformed or names no specific language.
   *
   * <p>The request's own locale cannot be used: {@code LocaleFilter} replaces it with the app
   * language.
   */
  public static Locale regionalLocaleFrom(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return null;
    }
    try {
      return Locale.LanguageRange.parse(acceptLanguage).stream()
          .filter(range -> range.getWeight() > 0 && !range.getRange().contains("*"))
          .map(range -> Locale.forLanguageTag(range.getRange()))
          .filter(locale -> !locale.getLanguage().isEmpty())
          .findFirst()
          .orElse(null);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  /**
   * Formats structured booking data as an HTML-safe message, with root-relative links, for display
   * inside RSpace in the given recipient zone.
   */
  public String format(
      NotificationType type, BookingNotificationData data, ZoneId zone, Locale locale) {
    return format(type, data, zone, locale, null, "");
  }

  /**
   * Formats structured booking data as {@link #format(NotificationType, BookingNotificationData,
   * ZoneId, Locale)} does, but writes times with the 12- or 24-hour clock of {@code
   * regionalLocale}, the viewer's browser region, when it is not null. Words stay in {@code
   * locale}, as on the booking pages.
   */
  public String format(
      NotificationType type,
      BookingNotificationData data,
      ZoneId zone,
      Locale locale,
      Locale regionalLocale) {
    return format(type, data, zone, locale, regionalLocale, "");
  }

  /**
   * Formats structured booking data as an HTML-safe message, with absolute links, for delivery
   * outside RSpace, such as email, in the given recipient zone.
   */
  public String formatForEmail(
      NotificationType type, BookingNotificationData data, ZoneId zone, Locale locale) {
    return format(type, data, zone, locale, null, absoluteLinkBase);
  }

  private String format(
      NotificationType type,
      BookingNotificationData data,
      ZoneId zone,
      Locale locale,
      Locale regionalLocale,
      String linkBase) {
    if (!isBookingNotification(type)) {
      throw new IllegalArgumentException("Unsupported booking notification type: " + type);
    }
    return messageSource.getMessage(
        messageKey(type),
        new Object[] {
          StringEscapeUtils.escapeHtml4(data.getBookingId()),
          StringEscapeUtils.escapeHtml4(data.getInstrumentName()),
          StringEscapeUtils.escapeHtml4(data.getInstrumentGlobalIdentifier()),
          formatInstant(data.getStartTime(), zone, locale, regionalLocale),
          formatInstant(data.getEndTime(), zone, locale, regionalLocale),
          href(linkBase, BOOKING_PATH, data.getBookingId()),
          href(linkBase, BOOKABLE_ITEM_PATH, data.getInstrumentGlobalIdentifier())
        },
        locale);
  }

  private static String href(String linkBase, String path, String identifier) {
    return StringEscapeUtils.escapeHtml4(
        linkBase + path + UriUtils.encodePathSegment(identifier, StandardCharsets.UTF_8));
  }

  private static String stripTrailingSlashes(String value) {
    String trimmed = value == null ? "" : value.trim();
    int end = trimmed.length();
    while (end > 0 && trimmed.charAt(end - 1) == '/') {
      end--;
    }
    return trimmed.substring(0, end);
  }

  /**
   * Formats an older stored booking message if its final interval is still in canonical ISO form.
   */
  public String formatLegacy(NotificationType type, String message, ZoneId zone, Locale locale) {
    return formatLegacy(type, message, zone, locale, null);
  }

  /**
   * Formats an older stored booking message as {@link #formatLegacy(NotificationType, String,
   * ZoneId, Locale)} does, with the 12- or 24-hour clock of {@code regionalLocale} when it is not
   * null.
   */
  public String formatLegacy(
      NotificationType type, String message, ZoneId zone, Locale locale, Locale regionalLocale) {
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
        + formatInstant(start, zone, locale, regionalLocale)
        + " to "
        + formatInstant(end, zone, locale, regionalLocale)
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

  private static String formatInstant(
      String value, ZoneId zone, Locale locale, Locale regionalLocale) {
    Instant instant = Instant.parse(value);
    String localDateTime =
        regionalLocale == null
            ? DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zone)
                .format(instant)
            : formatWithHourCycleOf(LocalDateTime.ofInstant(instant, zone), locale, regionalLocale);
    String offset = zone.getRules().getOffset(instant).getId();
    return localDateTime + " (" + zone.getId() + ", UTC" + ("Z".equals(offset) ? "" : offset) + ")";
  }

  /**
   * Formats a wall-clock time in {@code locale}'s words and field order with {@code
   * regionalLocale}'s 12- or 24-hour clock, as the booking pages do with the browser's regional
   * format. java.time ignores the Unicode {@code hc} keyword, so this uses ICU, which also backs
   * the browser's {@code Intl} API.
   */
  private static String formatWithHourCycleOf(
      LocalDateTime wallClock, Locale locale, Locale regionalLocale) {
    ULocale withHourCycle =
        new ULocale.Builder()
            .setLocale(ULocale.forLocale(locale))
            .setUnicodeLocaleKeyword("hc", hourCycleKeyword(regionalLocale))
            .build();
    // ICU formatters are not thread-safe, so each call builds its own. The wall clock is already
    // in the display zone, so ICU formats it in GMT and never maps the zone to its own tz data.
    DateFormat formatter =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, withHourCycle);
    formatter.setTimeZone(TimeZone.GMT_ZONE);
    return formatter.format(Date.from(wallClock.toInstant(ZoneOffset.UTC)));
  }

  private static String hourCycleKeyword(Locale regionalLocale) {
    return switch (DateTimePatternGenerator.getInstance(ULocale.forLocale(regionalLocale))
        .getDefaultHourCycle()) {
      case HOUR_CYCLE_11 -> "h11";
      case HOUR_CYCLE_12 -> "h12";
      case HOUR_CYCLE_23 -> "h23";
      case HOUR_CYCLE_24 -> "h24";
    };
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
