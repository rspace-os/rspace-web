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
  void canonicalDefaultsMatchTheLiquibaseColumnDefaults() {
    assertEquals(
        "[1,2,3,4,5,6,7]",
        BookingOpeningHoursCodec.openDaysJson(BookingSchedulingSettings.DEFAULT_OPEN_DAYS));
    assertEquals(
        "[]",
        BookingOpeningHoursCodec.openingExceptionsJson(
            BookingSchedulingSettings.DEFAULT_OPENING_EXCEPTIONS));
  }

  @Test
  void roundTripsSortedValuesThroughStoredJson() {
    List<BookingOpeningException> exceptions =
        List.of(
            new BookingOpeningException(6, "10:00", "16:00"),
            new BookingOpeningException(2, "00:00", "24:00"));
    String json = BookingOpeningHoursCodec.openingExceptionsJson(exceptions);

    assertEquals(
        "[{\"dayOfWeek\":2,\"start\":\"00:00\",\"end\":\"24:00\"},"
            + "{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\"}]",
        json);
    assertEquals(
        List.of(
            new BookingOpeningException(2, "00:00", "24:00"),
            new BookingOpeningException(6, "10:00", "16:00")),
        BookingOpeningHoursCodec.openingExceptions(json));
    assertEquals("[1,3,7]", BookingOpeningHoursCodec.openDaysJson(List.of(7, 1, 3)));
    assertEquals(List.of(1, 3, 7), BookingOpeningHoursCodec.openDays("[7,3,1]"));
  }

  @Test
  void convertersStoreCanonicalJsonAndKeepEnversDeletionNulls() {
    BookingOpenDaysConverter days = new BookingOpenDaysConverter();
    BookingOpeningExceptionsConverter exceptions = new BookingOpeningExceptionsConverter();

    assertEquals("[2,4]", days.convertToDatabaseColumn(List.of(4, 2)));
    assertEquals(List.of(2, 4), days.convertToEntityAttribute("[2,4]"));
    assertEquals("[]", exceptions.convertToDatabaseColumn(List.of()));
    assertEquals(List.of(), exceptions.convertToEntityAttribute("[]"));
    assertNull(days.convertToDatabaseColumn(null));
    assertNull(days.convertToEntityAttribute(null));
    assertNull(exceptions.convertToEntityAttribute(null));
    assertThrows(IllegalArgumentException.class, () -> days.convertToEntityAttribute("[8]"));
    assertThrows(
        IllegalArgumentException.class, () -> exceptions.convertToEntityAttribute("not json"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "[1,2,3,4,5,6,7,1]",
        "[1,1]",
        "[0]",
        "[8]",
        "[1.5]",
        "[1.0]",
        "[\"1\"]",
        "[true]",
        "[null]",
        "[[1]]",
        "[{\"day\":1}]",
        "[2147483648]",
        "1",
        "\"1,2\"",
        "{}",
        "null"
      })
  void rejectsInvalidOpenDays(String json) throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> BookingOpeningHoursCodec.openDays(mapper.readTree(json)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "[null]",
        "[1]",
        "[{\"dayOfWeek\":1,\"start\":\"09:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"17:00\",\"note\":\"x\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"09:00\",\"close\":\"17:00\"}]",
        "[{\"dayOfWeek\":\"1\",\"start\":\"09:00\",\"end\":\"17:00\"}]",
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
        "[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"24:00\"}]",
        "[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":\"17:00\"},"
            + "{\"dayOfWeek\":1,\"start\":\"10:00\",\"end\":\"16:00\"}]"
      })
  void rejectsInvalidOpeningExceptions(String json) throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () -> BookingOpeningHoursCodec.openingExceptions(mapper.readTree(json)));
  }

  @Test
  void acceptsAnExceptionOnEveryWeekdayIncludingAllDayHours() throws Exception {
    StringBuilder json = new StringBuilder("[");
    for (int day = 7; day >= 1; day--) {
      json.append("{\"dayOfWeek\":").append(day).append(",\"start\":\"00:00\",\"end\":\"24:00\"}");
      json.append(day == 1 ? "]" : ",");
    }

    List<BookingOpeningException> decoded =
        BookingOpeningHoursCodec.openingExceptions(mapper.readTree(json.toString()));

    assertEquals(7, decoded.size());
    assertEquals(1, decoded.get(0).dayOfWeek());
    assertEquals(7, decoded.get(6).dayOfWeek());
  }
}
