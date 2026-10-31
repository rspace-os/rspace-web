package com.researchspace.webapp.controller;

import static java.util.stream.Collectors.joining;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Readable "Label: value" details and a display name of a booking audit snapshot for the My RSpace
 * audit table and its CSV export. Labels and value formatting match the bookable item's audit
 * history; instants are shown to the second.
 */
@Slf4j
final class BookingAuditDetails {

  private static final Pattern ISO_INSTANT =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z");

  private static final Pattern PERMANENT_DELETE =
      Pattern.compile("(?:^|;)\\s*permanent=true(?:;|$)");
  private static final String LABEL_PREFIX = "booking:bookableItemDetails.audit.values.";

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
          "maxBookingDurationMinutes",
          "bufferBeforeMinutes",
          "bufferAfterMinutes",
          "allowDoubleBooking",
          "availabilityWindowStart",
          "availabilityWindowEnd",
          "timezoneMode",
          "customTimezone",
          "timeFormat",
          "defaultSharedWith",
          "configurationVersion",
          "removedBookings",
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

  // Recorded enum values, worded with the phrases the booking UI already uses.
  private static final Map<Object, String> DISPLAY_VALUES =
      Map.of(
          "CONFIRMED", "booking:bookings.details.confirmed",
          "CANCELLED", "booking:bookings.details.cancelled",
          "ACTIVE", "booking:bookableItems.states.active",
          "ARCHIVED", "booking:bookableItemDetails.archived",
          "BOOKING", "booking:bookings.form.typeBooking",
          "MAINTENANCE", "booking:calendar.feed.maintenance");

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
    String label =
        switch (action) {
          case CREATE -> "export.audit.csv.bookingActionCreated";
          case WRITE -> "export.audit.csv.bookingActionChanged";
          case RESTORE -> "export.audit.csv.bookingActionRestored";
          default -> null;
        };
    return label == null ? action.toString() : messages.getMessageForLocale(label, locale);
  }

  static boolean isBookingEvent(HistoricData event) {
    return AuditDomain.BOOKING.equals(event.getDomain());
  }

  /**
   * Sets {@link HistoricData#getDetails()} and {@link HistoricData#getDisplayName()} on every
   * booking event in the results.
   */
  static void addTo(
      List<AuditTrailSearchResult> results, MessageSourceUtils messages, Locale locale) {
    for (AuditTrailSearchResult result : results) {
      Map<String, Object> data = payload(result.getEvent());
      if (isBookingEvent(result.getEvent())) {
        result.getEvent().setDetails(format(data, messages, locale, true));
        result.getEvent().setDisplayName(displayName(data));
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
   * states, event kinds and booleans. The CSV keeps the raw values as its stable format.
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
                    messages.getMessageForLocale(
                            LABEL_PREFIX + RENAMED_LABELS.getOrDefault(key, key), locale)
                        + ": "
                        + value(key, data.get(key), locale, messages, forDisplay));
    Stream<String> other =
        data.keySet().stream()
            .filter(key -> !"id".equals(key) && !LABEL_KEYS.contains(key))
            .sorted()
            .map(key -> key + ": " + value(key, data.get(key), locale, messages, forDisplay));
    return Stream.concat(known, other).collect(joining("; "));
  }

  /** Details followed by the recorded description, each omitted when blank. */
  static String combine(String details, String recorded) {
    return Stream.of(details, recorded).filter(StringUtils::isNotBlank).collect(joining("; "));
  }

  private static String value(
      String key, Object value, Locale locale, MessageSourceUtils messages, boolean forDisplay) {
    if (value == null) {
      return "—";
    }
    if (forDisplay && value instanceof Boolean flag) {
      return messages.getMessageForLocale(
          flag ? "common:actions.yes" : "common:actions.no", locale);
    }
    if (forDisplay
        && ("state".equals(key) || "kind".equals(key))
        && DISPLAY_VALUES.containsKey(value)) {
      return messages.getMessageForLocale(DISPLAY_VALUES.get(value), locale);
    }
    if (("start".equals(key) || "end".equals(key)) && value instanceof Number epochMillis) {
      return Instant.ofEpochMilli(epochMillis.longValue())
          .truncatedTo(ChronoUnit.SECONDS)
          .toString();
    }
    if (!"purpose".equals(key)
        && !"cancellationReason".equals(key)
        && value instanceof String text
        && ISO_INSTANT.matcher(text).matches()) {
      return toSeconds(text);
    }
    String targetGlobalId = "target".equals(key) ? targetGlobalId(value) : null;
    if (targetGlobalId != null) {
      return targetGlobalId;
    }
    if ("openingHours".equals(key)
        && value instanceof List<?> openingHours
        && openingHours.stream().allMatch(BookingAuditDetails::isOpeningHoursEntry)) {
      return openingHours.isEmpty()
          ? "—"
          : openingHours.stream()
              .map(Map.class::cast)
              .map(
                  entry ->
                      weekday(entry.get("dayOfWeek"), locale)
                          + " "
                          + entry.get("start")
                          + "–"
                          + displayEnd((String) entry.get("end")))
              .collect(joining(", "));
    }
    return value instanceof Map || value instanceof List
        ? JacksonUtil.toJson(value)
        : value.toString();
  }

  private static String targetGlobalId(Object value) {
    return value instanceof Map<?, ?> target
            && "INSTRUMENT".equals(target.get("type"))
            && target.get("id") instanceof Number id
        ? "IN" + id
        : null;
  }

  // Snapshots record Instant.toString(), which keeps every fraction down to nanoseconds.
  private static String toSeconds(String instant) {
    try {
      return Instant.parse(instant).truncatedTo(ChronoUnit.SECONDS).toString();
    } catch (DateTimeParseException ex) {
      log.warn(
          "Audit value {} looks like an instant but does not parse; shown as recorded", instant);
      return instant;
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
