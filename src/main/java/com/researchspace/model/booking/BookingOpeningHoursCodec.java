package com.researchspace.model.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Strict JSON form of {@code openDays} and {@code openingExceptions}, shared by the REST API and
 * persistence.
 *
 * <p>Each decoder validates one complete JSON value on its own: its kind, every entry and the
 * uniqueness of weekdays. It never coerces strings, fractions or booleans to weekdays. Rules that
 * span both fields, such as an exception day having to be open, belong to the settings validation
 * that runs on the merged result. Decoded values are immutable and sorted by weekday.
 */
public final class BookingOpeningHoursCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> EXCEPTION_FIELDS = Set.of("dayOfWeek", "start", "end");

  private BookingOpeningHoursCodec() {}

  /** Decodes a complete {@code openDays} array or throws {@link IllegalArgumentException}. */
  public static List<Integer> openDays(JsonNode node) {
    if (node == null || !node.isArray() || node.isEmpty() || node.size() > 7) {
      throw new IllegalArgumentException("Open days must be an array of one to seven weekdays");
    }
    List<Integer> days = new ArrayList<>();
    for (JsonNode entry : node) {
      days.add(weekday(entry));
    }
    if (!BookingSchedulingSettings.areOpenDaysValid(days)) {
      throw new IllegalArgumentException("Open days must be unique ISO weekdays");
    }
    return days.stream().sorted().toList();
  }

  /**
   * Decodes a complete {@code openingExceptions} array or throws {@link IllegalArgumentException}.
   */
  public static List<BookingOpeningException> openingExceptions(JsonNode node) {
    if (node == null || !node.isArray() || node.size() > 7) {
      throw new IllegalArgumentException("Opening exceptions must be an array of at most seven");
    }
    List<BookingOpeningException> exceptions = new ArrayList<>();
    for (JsonNode entry : node) {
      exceptions.add(exception(entry));
    }
    if (!BookingSchedulingSettings.areOpeningExceptionsValid(exceptions)) {
      throw new IllegalArgumentException("Opening exceptions must have valid, unique weekdays");
    }
    return exceptions.stream()
        .sorted(Comparator.comparingInt(BookingOpeningException::dayOfWeek))
        .toList();
  }

  /** Decodes stored {@code openDays} JSON, validating it like API input. */
  public static List<Integer> openDays(String json) {
    return openDays(read(json));
  }

  /** Decodes stored {@code openingExceptions} JSON, validating it like API input. */
  public static List<BookingOpeningException> openingExceptions(String json) {
    return openingExceptions(read(json));
  }

  /** Canonical JSON, for example {@code [1,2,3,4,5,6,7]}. */
  public static String openDaysJson(List<Integer> days) {
    ArrayNode array = MAPPER.createArrayNode();
    days.stream().sorted().forEach(array::add);
    return array.toString();
  }

  /** Canonical JSON, for example {@code [{"dayOfWeek":6,"start":"10:00","end":"16:00"}]}. */
  public static String openingExceptionsJson(List<BookingOpeningException> exceptions) {
    ArrayNode array = MAPPER.createArrayNode();
    exceptions.stream()
        .sorted(Comparator.comparingInt(BookingOpeningException::dayOfWeek))
        .forEach(
            exception ->
                array
                    .addObject()
                    .put("dayOfWeek", exception.dayOfWeek())
                    .put("start", exception.start())
                    .put("end", exception.end()));
    return array.toString();
  }

  private static JsonNode read(String json) {
    if (json == null) {
      throw new IllegalArgumentException("Opening-hours JSON must not be null");
    }
    try {
      return MAPPER.readTree(json);
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Opening-hours JSON is malformed", ex);
    }
  }

  private static int weekday(JsonNode node) {
    if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
      throw new IllegalArgumentException("A weekday must be an integer");
    }
    int day = node.intValue();
    if (day < 1 || day > 7) {
      throw new IllegalArgumentException("A weekday must be from 1 to 7");
    }
    return day;
  }

  private static BookingOpeningException exception(JsonNode node) {
    if (node == null || !node.isObject() || node.size() != EXCEPTION_FIELDS.size()) {
      throw new IllegalArgumentException("An opening exception must be a complete object");
    }
    Set<String> names = new HashSet<>();
    for (Iterator<String> iterator = node.fieldNames(); iterator.hasNext(); ) {
      names.add(iterator.next());
    }
    if (!names.equals(EXCEPTION_FIELDS)
        || !node.get("start").isTextual()
        || !node.get("end").isTextual()) {
      throw new IllegalArgumentException("An opening exception has unknown or invalid fields");
    }
    return new BookingOpeningException(
        weekday(node.get("dayOfWeek")), node.get("start").textValue(), node.get("end").textValue());
  }
}
