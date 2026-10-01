package com.researchspace.api.v2.controller;

import com.ibm.icu.text.ListFormatter;
import com.researchspace.service.ListFormatUtils;
import com.researchspace.service.MessageSourceUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A whole-minute duration in words, such as "2 hours" or "1 hour, 30 minutes", split into days,
 * hours and minutes and joined as a unit list. It follows the booking form's {@code
 * formatDurationMinutes}, so a problem detail names a limit exactly as the form does.
 */
final class BookingDurationWords {

  private static final long MINUTES_PER_HOUR = 60;
  private static final long MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

  private BookingDurationWords() {}

  static String format(long totalMinutes, MessageSourceUtils messages, Locale locale) {
    List<String> parts = new ArrayList<>();
    addPart(parts, "duration.days", totalMinutes / MINUTES_PER_DAY, messages, locale);
    addPart(
        parts,
        "duration.hours",
        totalMinutes % MINUTES_PER_DAY / MINUTES_PER_HOUR,
        messages,
        locale);
    addPart(parts, "duration.minutes", totalMinutes % MINUTES_PER_HOUR, messages, locale);
    if (parts.isEmpty()) {
      parts.add(messages.getMessage("duration.minutes", new Object[] {0L}, locale));
    }
    return ListFormatUtils.formatList(parts, locale, ListFormatter.Type.UNITS);
  }

  private static void addPart(
      List<String> parts, String key, long value, MessageSourceUtils messages, Locale locale) {
    if (value > 0) {
      parts.add(messages.getMessage(key, new Object[] {value}, locale));
    }
  }
}
