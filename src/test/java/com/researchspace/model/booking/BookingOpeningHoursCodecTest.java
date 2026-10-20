package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookingOpeningHoursCodecTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void defaultsContainSevenIndependentFullDays() {
    String json =
        BookingOpeningHoursCodec.openingHoursJson(BookingSchedulingSettings.DEFAULT_OPENING_HOURS);
    assertEquals(
        BookingSchedulingSettings.DEFAULT_OPENING_HOURS,
        BookingOpeningHoursCodec.openingHours(json));
  }

  @Test
  void roundTripsSortedValuesAndKeepsEnversDeletionNulls() {
    BookingOpeningHoursConverter converter = new BookingOpeningHoursConverter();
    List<BookingOpeningHoursEntry> hours =
        List.of(
            new BookingOpeningHoursEntry(6, "10:00", "16:00"),
            new BookingOpeningHoursEntry(2, "00:00", "24:00"));
    String json = converter.convertToDatabaseColumn(hours);
    assertEquals(
        "[{\"dayOfWeek\":2,\"start\":\"00:00\",\"end\":\"24:00\"},"
            + "{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\"}]",
        json);
    assertEquals(List.of(hours.get(1), hours.get(0)), converter.convertToEntityAttribute(json));
    assertNull(converter.convertToDatabaseColumn(null));
    assertNull(converter.convertToEntityAttribute(null));
    assertThrows(
        IllegalArgumentException.class, () -> converter.convertToEntityAttribute("not json"));
    assertThrows(
        IllegalArgumentException.class, () -> converter.convertToDatabaseColumn(List.of()));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "null",
        "[]",
        "[[{}]]",
        "{}",
        "[null]",
        "[1]",
        "[{\"dayOfWeek\":1,\"start\":\"09:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"17:00\",\"note\":\"x\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"09:00\",\"close\":\"17:00\"}]",
        "[{\"dayOfWeek\":\"1\",\"start\":\"09:00\",\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":1.0,\"start\":\"09:00\",\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":1.5,\"start\":\"09:00\",\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":true,\"start\":\"09:00\",\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":null,\"start\":\"09:00\",\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":0,\"start\":\"09:00\",\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":8,\"start\":\"09:00\",\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":null,\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":900,\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"9:00\",\"end\":\"17:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"17:00\",\"end\":\"09:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"09:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"24:00\",\"end\":\"24:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"17:00\"},"
            + "{\"dayOfWeek\":1,\"start\":\"10:00\",\"end\":\"16:00\"}]"
      })
  void rejectsInvalidSchedules(String json) throws Exception {
    var node = mapper.readTree(json);
    assertThrows(IllegalArgumentException.class, () -> BookingOpeningHoursCodec.openingHours(node));
  }

  @Test
  void acceptsAllSevenWeekdaysIncludingAllDayHours() throws Exception {
    StringBuilder json = new StringBuilder("[");
    for (int day = 7; day >= 1; day--) {
      json.append("{\"dayOfWeek\":").append(day).append(",\"start\":\"00:00\",\"end\":\"24:00\"}");
      json.append(day == 1 ? "]" : ",");
    }

    List<BookingOpeningHoursEntry> decoded =
        BookingOpeningHoursCodec.openingHours(mapper.readTree(json.toString()));

    assertEquals(7, decoded.size());
    assertEquals(1, decoded.get(0).dayOfWeek());
    assertEquals(7, decoded.get(6).dayOfWeek());
  }

  @Test
  void acceptsClosingAtMidnightAfterAPartialDayStart() throws Exception {
    assertEquals(
        List.of(new BookingOpeningHoursEntry(1, "18:00", "24:00")),
        BookingOpeningHoursCodec.openingHours(
            mapper.readTree("[{\"dayOfWeek\":1,\"start\":\"18:00\",\"end\":\"24:00\"}]")));
  }
}
