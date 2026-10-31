package com.researchspace.webapp.controller;

import static java.util.Map.entry;
import static java.util.stream.Collectors.joining;

import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

/**
 * Readable "Label: value" details of a booking audit snapshot for the My RSpace audit table and its
 * CSV export. Labels and value formatting match the bookable item's audit history.
 */
final class BookingAuditDetails {

  private static final String LABEL_PREFIX = "booking:bookableItemDetails.audit.values.";

  // Snapshot key to label key, in display order; other keys follow alphabetically, unlabelled.
  private static final List<Map.Entry<String, String>> LABELS =
      List.of(
          entry("start", "start"),
          entry("end", "end"),
          entry("kind", "kind"),
          entry("purpose", "purpose"),
          entry("state", "state"),
          entry("target", "target"),
          entry("targetName", "targetName"),
          entry("bookingConfigurationId", "configuration"),
          entry("enabled", "enabled"),
          entry("timezone", "timezone"),
          entry("openingHours", "openingHours"),
          entry("slotGranularityMinutes", "increment"),
          entry("maxBookingDurationMinutes", "maximumDuration"),
          entry("bufferBeforeMinutes", "bufferBefore"),
          entry("bufferAfterMinutes", "bufferAfter"),
          entry("allowDoubleBooking", "allowDoubleBooking"),
          entry("availabilityWindowStart", "availabilityWindowStart"),
          entry("availabilityWindowEnd", "availabilityWindowEnd"),
          entry("timezoneMode", "timezoneMode"),
          entry("customTimezone", "customTimezone"),
          entry("timeFormat", "timeFormat"),
          entry("defaultSharedWith", "defaultSharedWith"),
          entry("configurationVersion", "configurationVersion"),
          entry("removedBookings", "removedBookings"),
          entry("removedSubscriptions", "removedSubscriptions"),
          entry("deletedAt", "deletedAt"));

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

  static boolean isBookingEvent(HistoricData event) {
    return AuditDomain.BOOKING.equals(event.getDomain());
  }

  /** Sets {@link HistoricData#getDetails()} on every booking event in the results. */
  static void addTo(
      List<AuditTrailSearchResult> results, MessageSourceUtils messages, Locale locale) {
    for (AuditTrailSearchResult result : results) {
      Map<String, Object> data = payload(result.getEvent());
      if (isBookingEvent(result.getEvent())) {
        result.getEvent().setDetails(format(data, messages, locale, true));
      }
    }
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
        LABELS.stream()
            .filter(label -> data.containsKey(label.getKey()))
            .map(
                label ->
                    messages.getMessageForLocale(LABEL_PREFIX + label.getValue(), locale)
                        + ": "
                        + value(
                            label.getKey(),
                            data.get(label.getKey()),
                            locale,
                            messages,
                            forDisplay));
    Stream<String> other =
        data.keySet().stream()
            .filter(
                key ->
                    !"id".equals(key)
                        && LABELS.stream().noneMatch(label -> label.getKey().equals(key)))
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
      return Instant.ofEpochMilli(epochMillis.longValue()).toString();
    }
    if ("target".equals(key)
        && value instanceof Map<?, ?> target
        && "INSTRUMENT".equals(target.get("type"))
        && target.get("id") instanceof Number id) {
      return "IN" + id;
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
