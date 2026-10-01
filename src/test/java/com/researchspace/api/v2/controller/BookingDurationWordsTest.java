package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Mirrors the booking form's {@code formatDurationMinutes} cases. */
class BookingDurationWordsTest {

  private final MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "0|0 minutes",
        "1|1 minute",
        "45|45 minutes",
        "60|1 hour",
        "90|1 hour, 30 minutes",
        "120|2 hours",
        "1440|1 day",
        "1470|1 day, 30 minutes",
        "1501|1 day, 1 hour, 1 minute",
        "527040|366 days"
      })
  void spellsOutDaysHoursAndMinutes(long minutes, String expected) {
    assertEquals(expected, BookingDurationWords.format(minutes, messages, Locale.US));
  }
}
