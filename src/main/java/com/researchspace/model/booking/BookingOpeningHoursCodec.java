package com.researchspace.model.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.collection.CollectionFieldType;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.CollectionFieldTypes.Constraints;
import com.researchspace.model.collection.DeclaredFieldType;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Shared declared fields and strict JSON codec for complete weekly booking schedules. */
public final class BookingOpeningHoursCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  public static final String WALL_TIME_PATTERN = "^(?:[01]\\d|2[0-3]):[0-5]\\d$";
  public static final String CLOSING_TIME_PATTERN = "^(?:(?:[01]\\d|2[0-3]):[0-5]\\d|24:00)$";

  public static final DeclaredFieldType<List<BookingOpeningHoursEntry>> TYPE =
      CollectionFieldTypes.array(
          BookingOpeningHoursEntry.class,
          List.of(
              Field.embedded(
                      "dayOfWeek",
                      CollectionFieldTypes.constrained(
                          CollectionFieldTypes.integer(),
                          new Constraints(BigDecimal.ONE, BigDecimal.valueOf(7), null, null, null)),
                      BookingOpeningHoursEntry::dayOfWeek)
                  .required()
                  .documented(OpenApiSchemaDocumentation.of(null, "ISO weekday, Monday = 1.", "1")),
              Field.embedded("start", wallTime(WALL_TIME_PATTERN), BookingOpeningHoursEntry::start)
                  .required()
                  .documented(
                      OpenApiSchemaDocumentation.of(null, "Opening time in HH:mm.", "09:00")),
              Field.embedded("end", wallTime(CLOSING_TIME_PATTERN), BookingOpeningHoursEntry::end)
                  .required()
                  .documented(
                      OpenApiSchemaDocumentation.of(
                          null,
                          "Exclusive closing time in HH:mm; 24:00 is next midnight.",
                          "24:00"))),
          values ->
              new BookingOpeningHoursEntry(
                  (Integer) values.get("dayOfWeek"),
                  (String) values.get("start"),
                  (String) values.get("end")),
          entry -> {
            if (!BookingSchedulingSettings.isOpeningIntervalValid(entry.start(), entry.end())) {
              throw new IllegalArgumentException("Opening start must precede end");
            }
          },
          entries -> {
            if (!BookingSchedulingSettings.areOpeningHoursValid(entries)) {
              throw new IllegalArgumentException("Opening hours must have valid, unique weekdays");
            }
          },
          1,
          7);

  private BookingOpeningHoursCodec() {}

  private static CollectionFieldType<String> wallTime(String pattern) {
    return CollectionFieldTypes.constrained(
        CollectionFieldTypes.text(5),
        new Constraints(null, null, null, Pattern.compile(pattern), null));
  }

  /** Decodes a complete schedule with the same child and array rules as collection writes. */
  public static List<BookingOpeningHoursEntry> openingHours(JsonNode node) {
    if (node == null) {
      throw new IllegalArgumentException("Opening hours must not be null");
    }
    return TYPE.parse(node).stream()
        .sorted(Comparator.comparingInt(BookingOpeningHoursEntry::dayOfWeek))
        .toList();
  }

  /** Decodes stored opening-hours JSON, validating it like API input. */
  public static List<BookingOpeningHoursEntry> openingHours(String json) {
    if (json == null) {
      throw new IllegalArgumentException("Opening-hours JSON must not be null");
    }
    try {
      return openingHours(MAPPER.readTree(json));
    } catch (JsonProcessingException ex) {
      throw new IllegalArgumentException("Opening-hours JSON is malformed", ex);
    }
  }

  /** Validated canonical JSON with entries sorted by weekday. */
  public static String openingHoursJson(List<BookingOpeningHoursEntry> openingHours) {
    List<BookingOpeningHoursEntry> sorted =
        openingHours.stream()
            .sorted(Comparator.comparingInt(BookingOpeningHoursEntry::dayOfWeek))
            .toList();
    return MAPPER.valueToTree(TYPE.serialize(sorted)).toString();
  }
}
