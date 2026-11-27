package com.researchspace.webapp.controller;

import com.ibm.icu.text.ListFormatter;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.ListFormatUtils;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Readable details and a display name of a booking audit snapshot for the My RSpace audit table and
 * its CSV export. Labels and value formatting match the bookable item's audit history. The table
 * shows instants as localized UTC date-times to the second; the CSV keeps ISO instants.
 */
@Slf4j
final class BookingAuditDetails {

  private static final Pattern ISO_INSTANT =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z");

  private static final Pattern PERMANENT_DELETE =
      Pattern.compile("(?:^|;)\\s*permanent=true(?:;|$)");
  private static final String PERMANENT_DELETE_MARKER = "permanent=true";
  private static final String SUBJECT_PREFIX = "subject=";
  private static final String LABEL_PREFIX = "booking:bookableItemDetails.audit.values.";
  private static final String MESSAGE_PREFIX = "export.audit.booking.";

  private static final List<String> LABEL_ORDER =
      List.of(
          "start",
          "end",
          "kind",
          "purpose",
          "state",
          "cancellationReason",
          "target",
          "targetName",
          "bookingConfigurationId",
          "enabled",
          "timezone",
          "openingHours",
          "slotGranularityMinutes",
          "incrementUnit",
          "maxBookingDurationMinutes",
          "bufferBeforeMinutes",
          "bufferAfterMinutes",
          "allowDoubleBooking",
          "availabilityWindowStart",
          "availabilityWindowEnd",
          "timezoneMode",
          "customTimezone",
          "timeFormat",
          "configurationVersion",
          "removedBookings",
          "removedAssignments",
          "removedSubscriptions",
          "deletedAt");

  private static final Map<String, String> RENAMED_LABELS =
      Map.of(
          "bookingConfigurationId", "configuration",
          "slotGranularityMinutes", "increment",
          "maxBookingDurationMinutes", "maximumDuration",
          "bufferBeforeMinutes", "bufferBefore",
          "bufferAfterMinutes", "bufferAfter");

  private static final Set<String> LABEL_KEYS = Set.copyOf(LABEL_ORDER);

  private static final Set<String> WORDED_KEYS =
      Set.of("state", "kind", "incrementUnit", "timezoneMode", "timeFormat");

  // Recorded enum values, worded with the phrases the booking UI already uses where it has them.
  private static final Map<Object, String> DISPLAY_VALUES =
      Map.ofEntries(
          Map.entry("CONFIRMED", "booking:bookings.details.confirmed"),
          Map.entry("CANCELLED", "booking:bookings.details.cancelled"),
          Map.entry("ACTIVE", "booking:allBookableItems.states.active"),
          Map.entry("ARCHIVED", "booking:bookableItemDetails.archived"),
          Map.entry("BOOKING", "booking:bookings.form.typeBooking"),
          Map.entry("MAINTENANCE", "booking:bookings.form.typeBlockout"),
          Map.entry("MINUTES", "booking:settings.fields.incrementMinutes"),
          Map.entry("HOURS", "booking:settings.fields.incrementHours"),
          Map.entry("DAYS", "booking:settings.fields.incrementDays"),
          Map.entry("BROWSER", MESSAGE_PREFIX + "timezoneModeBrowser"),
          Map.entry("INSTITUTION", MESSAGE_PREFIX + "timezoneModeInstitution"),
          Map.entry("CUSTOM", MESSAGE_PREFIX + "timezoneModeCustom"),
          Map.entry("AUTOMATIC", MESSAGE_PREFIX + "timeFormatAutomatic"),
          Map.entry("H12", MESSAGE_PREFIX + "timeFormatTwelveHour"),
          Map.entry("H24", MESSAGE_PREFIX + "timeFormatTwentyFourHour"));

  private BookingAuditDetails() {}

  static String displayAction(
      AuditAction action,
      String description,
      Map<String, Object> data,
      boolean bookingEvent,
      MessageSourceUtils messages,
      Locale locale) {
    if (!bookingEvent) {
      return action.toString();
    }

    if (AuditAction.DELETE.equals(action)
        && description != null
        && PERMANENT_DELETE.matcher(description).find()) {
      return messages.getMessageForLocale(
          "export.audit.csv.bookingActionPermanentlyDeleted", locale);
    }

    Object state = data.get("state");
    if (AuditAction.WRITE.equals(action) && state != null && "CANCELLED".equals(state.toString())) {
      return messages.getMessageForLocale("export.audit.csv.bookingActionCancelled", locale);
    }
    if (AuditAction.DELETE.equals(action) && state != null && "ARCHIVED".equals(state.toString())) {
      return messages.getMessageForLocale("export.audit.csv.bookingActionArchived", locale);
    }
    // Booking code records no other actions; any other stays the raw action, as in other domains.
    String label =
        switch (action) {
          case CREATE -> "export.audit.csv.bookingActionCreated";
          case WRITE -> "export.audit.csv.bookingActionChanged";
          case DELETE -> "export.audit.csv.bookingActionDeleted";
          case RESTORE -> "export.audit.csv.bookingActionRestored";
          default -> null;
        };
    return label == null ? action.toString() : messages.getMessageForLocale(label, locale);
  }

  static boolean isBookingEvent(HistoricData event) {
    return AuditDomain.BOOKING.equals(event.getDomain());
  }

  /**
   * Sets {@link HistoricData#getDetails()}, the worded snapshot followed by the readable recorded
   * description, and {@link HistoricData#getDisplayName()} on every booking event in the results.
   */
  static void addTo(
      List<AuditTrailSearchResult> results, MessageSourceUtils messages, Locale locale) {
    for (AuditTrailSearchResult result : results) {
      HistoricData event = result.getEvent();
      if (isBookingEvent(event)) {
        Map<String, Object> data = payload(event);
        event.setDetails(
            combine(
                format(data, messages, locale, true),
                readableDescription(event.getDescription(), messages, locale),
                messages,
                locale));
        event.setDisplayName(displayName(data));
      }
    }
  }

  /**
   * What the Name column shows for a booking snapshot, which has no {@code name}: the recorded
   * {@code targetName} (permanent-deletion snapshots), else the target's global ID such as {@code
   * IN5}, else a booking's configuration ID; {@code null} when the snapshot has none of these.
   */
  static String displayName(Map<String, Object> data) {
    if (data.get("targetName") instanceof String name && StringUtils.isNotBlank(name)) {
      return name;
    }
    String target = targetGlobalId(data.get("target"));
    if (target != null) {
      return target;
    }
    return data.get("bookingConfigurationId") instanceof String configuration
            && StringUtils.isNotBlank(configuration)
        ? configuration
        : null;
  }

  static Map<String, Object> payload(HistoricData event) {
    return event.getData() == null || event.getData().getData() == null
        ? Map.of()
        : event.getData().getData();
  }

  /** The recorded snapshot except its identifier, or an empty string when there is nothing else. */
  static String format(Map<String, Object> data, MessageSourceUtils messages, Locale locale) {
    return format(data, messages, locale, false);
  }

  /**
   * As {@link #format(Map, MessageSourceUtils, Locale)}; {@code forDisplay} also words known
   * states, kinds, units, modes and booleans, and localizes instants and opening hours. The CSV
   * keeps the raw values as its stable format.
   */
  static String format(
      Map<String, Object> data, MessageSourceUtils messages, Locale locale, boolean forDisplay) {
    Stream<String> known =
        LABEL_ORDER.stream()
            .filter(
                key ->
                    data.containsKey(key)
                        && (!"cancellationReason".equals(key) || data.get(key) != null))
            .map(
                key ->
                    entry(
                        messages.getMessageForLocale(
                            LABEL_PREFIX + RENAMED_LABELS.getOrDefault(key, key), locale),
                        value(key, data.get(key), locale, messages, forDisplay),
                        messages,
                        locale));
    // Fields outside the current snapshots have no label, so they keep their recorded name.
    Stream<String> other =
        data.keySet().stream()
            .filter(
                key ->
                    !"id".equals(key)
                        && !"defaultSharedWith".equals(key)
                        && !LABEL_KEYS.contains(key))
            .sorted()
            .map(
                key ->
                    entry(
                        key,
                        value(key, data.get(key), locale, messages, forDisplay),
                        messages,
                        locale));
    return join(Stream.concat(known, other).toList(), messages, locale);
  }

  /**
   * The recorded description in words: a delegated change's {@code subject=<username>} names the
   * user it was made on behalf of, and the {@code permanent=true} marker, which the action already
   * states, is dropped. Other recorded text is kept.
   */
  static String readableDescription(String recorded, MessageSourceUtils messages, Locale locale) {
    if (StringUtils.isBlank(recorded)) {
      return "";
    }
    List<String> parts = new ArrayList<>();
    String subject = null;
    for (String part : recorded.split(";")) {
      String trimmed = part.trim();
      if (trimmed.startsWith(SUBJECT_PREFIX)) {
        subject = trimmed.substring(SUBJECT_PREFIX.length());
      } else if (!trimmed.isEmpty() && !PERMANENT_DELETE_MARKER.equals(trimmed)) {
        parts.add(trimmed);
      }
    }
    if (subject != null) {
      parts.add(messages.getMessage(MESSAGE_PREFIX + "onBehalfOf", new Object[] {subject}, locale));
    }
    return join(parts, messages, locale);
  }

  /** Details followed by the readable description, each omitted when blank. */
  static String combine(
      String details, String description, MessageSourceUtils messages, Locale locale) {
    return join(
        Stream.of(details, description).filter(StringUtils::isNotBlank).toList(), messages, locale);
  }

  private static String entry(
      String label, String value, MessageSourceUtils messages, Locale locale) {
    return messages.getMessage(MESSAGE_PREFIX + "entry", new Object[] {label, value}, locale);
  }

  private static String join(List<String> parts, MessageSourceUtils messages, Locale locale) {
    return String.join(messages.getMessageForLocale(MESSAGE_PREFIX + "separator", locale), parts);
  }

  private static String value(
      String key, Object value, Locale locale, MessageSourceUtils messages, boolean forDisplay) {
    if (value == null) {
      // A permanent deletion records no name when its instrument was already gone.
      return messages.getMessageForLocale(
          MESSAGE_PREFIX + ("targetName".equals(key) ? "targetUnavailable" : "emptyValue"), locale);
    }
    if (forDisplay && value instanceof Boolean flag) {
      return messages.getMessageForLocale(
          flag ? "common:actions.yes" : "common:actions.no", locale);
    }
    if (forDisplay && WORDED_KEYS.contains(key) && DISPLAY_VALUES.containsKey(value)) {
      return messages.getMessageForLocale(DISPLAY_VALUES.get(value), locale);
    }
    if (("start".equals(key) || "end".equals(key)) && value instanceof Number epochMillis) {
      return instant(Instant.ofEpochMilli(epochMillis.longValue()), locale, forDisplay);
    }
    if (!"purpose".equals(key)
        && !"cancellationReason".equals(key)
        && value instanceof String text
        && ISO_INSTANT.matcher(text).matches()) {
      return recordedInstant(text, locale, forDisplay);
    }
    String targetGlobalId = "target".equals(key) ? targetGlobalId(value) : null;
    if (targetGlobalId != null) {
      return targetGlobalId;
    }
    if ("openingHours".equals(key)
        && value instanceof List<?> openingHours
        && openingHours.stream().allMatch(BookingAuditDetails::isOpeningHoursEntry)) {
      return openingHours.isEmpty()
          ? messages.getMessageForLocale(MESSAGE_PREFIX + "emptyValue", locale)
          : ListFormatUtils.formatList(
              openingHours.stream()
                  .map(entry -> weekdayHours((Map<?, ?>) entry, messages, locale, forDisplay))
                  .toList(),
              locale,
              ListFormatter.Type.UNITS);
    }
    return value instanceof Map || value instanceof List
        ? JacksonUtil.toJson(value)
        : value.toString();
  }

  private static String weekdayHours(
      Map<?, ?> entry, MessageSourceUtils messages, Locale locale, boolean forDisplay) {
    return messages.getMessage(
        MESSAGE_PREFIX + "weekdayHours",
        new Object[] {
          weekday(entry.get("dayOfWeek"), locale),
          wallTime((String) entry.get("start"), locale, forDisplay),
          wallTime(displayEnd((String) entry.get("end")), locale, forDisplay)
        },
        locale);
  }

  private static String targetGlobalId(Object value) {
    return value instanceof Map<?, ?> target
            && "INSTRUMENT".equals(target.get("type"))
            && target.get("id") instanceof Number id
        ? "IN" + id
        : null;
  }

  // Snapshots record Instant.toString(), which keeps every fraction down to nanoseconds.
  private static String recordedInstant(String instant, Locale locale, boolean forDisplay) {
    try {
      return instant(Instant.parse(instant), locale, forDisplay);
    } catch (DateTimeParseException ex) {
      log.warn(
          "Audit value {} looks like an instant but does not parse; shown as recorded", instant);
      return instant;
    }
  }

  /** The labels say UTC, so the table shows a localized UTC date-time; the CSV keeps ISO text. */
  private static String instant(Instant instant, Locale locale, boolean forDisplay) {
    Instant seconds = instant.truncatedTo(ChronoUnit.SECONDS);
    return forDisplay
        ? DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneOffset.UTC)
            .format(seconds)
        : seconds.toString();
  }

  /** An {@code HH:mm} wall time in {@code locale}'s clock for display, as recorded for the CSV. */
  private static String wallTime(String time, Locale locale, boolean forDisplay) {
    if (!forDisplay) {
      return time;
    }
    try {
      return LocalTime.parse(time)
          .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale));
    } catch (DateTimeParseException ex) {
      log.warn("Audit opening time {} does not parse; shown as recorded", time);
      return time;
    }
  }

  // Read-outs print a closing midnight as 00:00.
  private static String displayEnd(String end) {
    return "24:00".equals(end) ? "00:00" : end;
  }

  private static boolean isWeekday(Object day) {
    return day instanceof Integer number && number >= 1 && number <= 7;
  }

  private static boolean isOpeningHoursEntry(Object entry) {
    return entry instanceof Map<?, ?> map
        && isWeekday(map.get("dayOfWeek"))
        && map.get("start") instanceof String
        && map.get("end") instanceof String;
  }

  private static String weekday(Object day, Locale locale) {
    return DayOfWeek.of((Integer) day).getDisplayName(TextStyle.FULL, locale);
  }
}
