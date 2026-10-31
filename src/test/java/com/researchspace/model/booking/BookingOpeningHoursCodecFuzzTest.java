package com.researchspace.model.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.code_intelligence.jazzer.mutation.annotation.NotNull;
import com.code_intelligence.jazzer.mutation.annotation.WithUtf8Length;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

class BookingOpeningHoursCodecFuzzTest {

  private static final int MAX_FUZZ_INPUT_BYTES = 1_024;
  private static final InvalidCase[] INVALID_CASES = InvalidCase.values();

  @FuzzTest(maxDuration = "30s")
  void decodesOrRejects(@NotNull @WithUtf8Length(min = 0, max = MAX_FUZZ_INPUT_BYTES) String json) {
    if (json.getBytes(StandardCharsets.UTF_8).length > MAX_FUZZ_INPUT_BYTES) {
      return;
    }

    List<BookingOpeningHoursEntry> decoded;
    try {
      decoded = BookingOpeningHoursCodec.openingHours(json);
    } catch (IllegalArgumentException expected) {
      return;
    }

    assertCanonicalRoundTrip(decoded);
  }

  @FuzzTest(maxDuration = "30s")
  void validSchedules(FuzzedDataProvider data) {
    InvalidCase invalidCase =
        INVALID_CASES[Byte.toUnsignedInt(data.consumeByte()) % INVALID_CASES.length];
    List<BookingOpeningHoursEntry> schedule = validSchedule(data);

    assertCanonicalRoundTrip(schedule);
    String invalidJson = invalidScheduleJson(invalidCase, schedule.get(0));
    assertThrows(
        IllegalArgumentException.class,
        () -> BookingOpeningHoursCodec.openingHours(invalidJson),
        invalidCase.name());
  }

  private static List<BookingOpeningHoursEntry> validSchedule(FuzzedDataProvider data) {
    int entryCount = 1 + Math.floorMod(data.consumeByte(), 7);
    List<Integer> weekdays = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7));
    for (int index = 0; index < entryCount; index++) {
      Collections.swap(weekdays, index, data.consumeInt(index, weekdays.size() - 1));
    }

    List<BookingOpeningHoursEntry> schedule = new ArrayList<>(entryCount);
    for (int index = 0; index < entryCount; index++) {
      int startMinute = data.consumeInt(0, 1_439);
      int endMinute = data.consumeInt(startMinute + 1, 1_440);
      schedule.add(
          new BookingOpeningHoursEntry(
              weekdays.get(index), wallTime(startMinute), wallTime(endMinute)));
    }
    return schedule;
  }

  private static String wallTime(int minuteOfDay) {
    if (minuteOfDay == 1_440) {
      return BookingSchedulingSettings.END_OF_DAY;
    }
    return String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
  }

  private static String invalidScheduleJson(
      InvalidCase invalidCase, BookingOpeningHoursEntry entry) {
    int day = entry.dayOfWeek();
    String start = entry.start();
    String end = entry.end();
    return switch (invalidCase) {
      case DUPLICATE_WEEKDAY -> scheduleJson(List.of(entry, entry));
      case WEEKDAY_ZERO -> scheduleJson(List.of(new BookingOpeningHoursEntry(0, start, end)));
      case WEEKDAY_EIGHT -> scheduleJson(List.of(new BookingOpeningHoursEntry(8, start, end)));
      case EMPTY_ARRAY -> "[]";
      // Eight entries necessarily duplicate a weekday because only seven weekdays exist.
      case OVERSIZED_ARRAY -> scheduleJson(Collections.nCopies(8, entry));
      case WRONG_ROOT_TYPE -> "{}";
      case WRONG_ROW_TYPE -> "[1]";
      case WRONG_WEEKDAY_TYPE -> "[{\"dayOfWeek\":\"1\",\"start\":\"09:00\",\"end\":\"10:00\"}]";
      case WRONG_START_TYPE -> "[{\"dayOfWeek\":1,\"start\":9,\"end\":\"10:00\"}]";
      case WRONG_END_TYPE -> "[{\"dayOfWeek\":1,\"start\":\"09:00\",\"end\":10}]";
      case MISSING_WEEKDAY -> "[{\"start\":\"" + start + "\",\"end\":\"" + end + "\"}]";
      case MISSING_START -> "[{\"dayOfWeek\":" + day + ",\"end\":\"" + end + "\"}]";
      case MISSING_END -> "[{\"dayOfWeek\":" + day + ",\"start\":\"" + start + "\"}]";
      case NULL_WEEKDAY ->
          "[{\"dayOfWeek\":null,\"start\":\"" + start + "\",\"end\":\"" + end + "\"}]";
      case NULL_START -> "[{\"dayOfWeek\":" + day + ",\"start\":null,\"end\":\"" + end + "\"}]";
      case NULL_END -> "[{\"dayOfWeek\":" + day + ",\"start\":\"" + start + "\",\"end\":null}]";
      case EQUAL_INTERVAL ->
          scheduleJson(List.of(new BookingOpeningHoursEntry(day, "09:00", "09:00")));
      case REVERSED_INTERVAL ->
          scheduleJson(List.of(new BookingOpeningHoursEntry(day, "10:00", "09:00")));
      case BAD_TIME -> scheduleJson(List.of(new BookingOpeningHoursEntry(day, "9:00", "10:00")));
      case START_AT_24_HOURS ->
          scheduleJson(List.of(new BookingOpeningHoursEntry(day, "24:00", "24:00")));
    };
  }

  private static String scheduleJson(List<BookingOpeningHoursEntry> schedule) {
    return schedule.stream()
        .map(
            entry ->
                "{\"dayOfWeek\":"
                    + entry.dayOfWeek()
                    + ",\"start\":\""
                    + entry.start()
                    + "\",\"end\":\""
                    + entry.end()
                    + "\"}")
        .collect(Collectors.joining(",", "[", "]"));
  }

  private static void assertCanonicalRoundTrip(List<BookingOpeningHoursEntry> schedule) {
    List<BookingOpeningHoursEntry> sorted =
        schedule.stream()
            .sorted(Comparator.comparingInt(BookingOpeningHoursEntry::dayOfWeek))
            .toList();
    String canonical = scheduleJson(sorted);
    assertEquals(canonical, BookingOpeningHoursCodec.openingHoursJson(schedule));
    List<BookingOpeningHoursEntry> decoded = BookingOpeningHoursCodec.openingHours(canonical);

    assertEquals(sorted, decoded);
    assertEquals(canonical, BookingOpeningHoursCodec.openingHoursJson(decoded));

    List<BookingOpeningHoursEntry> permuted = new ArrayList<>(schedule);
    Collections.reverse(permuted);
    assertEquals(canonical, BookingOpeningHoursCodec.openingHoursJson(permuted));
    assertEquals(sorted, BookingOpeningHoursCodec.openingHours(scheduleJson(permuted)));
  }

  private enum InvalidCase {
    DUPLICATE_WEEKDAY,
    WEEKDAY_ZERO,
    WEEKDAY_EIGHT,
    EMPTY_ARRAY,
    OVERSIZED_ARRAY,
    WRONG_ROOT_TYPE,
    WRONG_ROW_TYPE,
    WRONG_WEEKDAY_TYPE,
    WRONG_START_TYPE,
    WRONG_END_TYPE,
    MISSING_WEEKDAY,
    MISSING_START,
    MISSING_END,
    NULL_WEEKDAY,
    NULL_START,
    NULL_END,
    EQUAL_INTERVAL,
    REVERSED_INTERVAL,
    BAD_TIME,
    START_AT_24_HOURS
  }
}
