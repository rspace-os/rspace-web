package com.researchspace.testutils;

import com.researchspace.model.booking.BookingOpeningHoursEntry;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/** Independent weekday rows for booking test fixtures. */
public final class BookingTestSchedules {
  private BookingTestSchedules() {}

  public static List<BookingOpeningHoursEntry> hours(String start, String end, int... weekdays) {
    IntStream days = weekdays.length == 0 ? IntStream.rangeClosed(1, 7) : Arrays.stream(weekdays);
    return days.mapToObj(day -> new BookingOpeningHoursEntry(day, start, end)).toList();
  }
}
