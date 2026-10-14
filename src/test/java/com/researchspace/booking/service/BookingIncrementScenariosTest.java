package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingIncrementUnit;
import com.researchspace.model.booking.BookingOpeningHoursCodec;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Runs the increment vectors shared with the frontend suite against the server rules. */
class BookingIncrementScenariosTest {

  private static final JsonNode SCENARIOS = load();

  private final BookingSchedulingPolicy policy = new BookingSchedulingPolicyImpl();

  static Stream<Arguments> intervals() {
    return named(SCENARIOS.get("intervals"));
  }

  static Stream<Arguments> settings() {
    return named(SCENARIOS.get("settings"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("intervals")
  void intervalVector(String name, JsonNode vector) {
    BookingConfiguration item = new BookingConfiguration();
    item.setTimeZone(vector.get("timezone").asText());
    item.setSlotGranularityMinutes(vector.get("slotGranularityMinutes").asLong());
    item.setIncrementUnit(BookingIncrementUnit.valueOf(vector.get("incrementUnit").asText()));
    item.setAlignToIncrement(vector.get("alignToIncrement").asBoolean());
    Date start = Date.from(Instant.parse(vector.get("start").asText()));
    Date end = Date.from(Instant.parse(vector.get("end").asText()));

    String expected = vector.get("expected").asText();
    if ("valid".equals(expected)) {
      assertDoesNotThrow(() -> policy.validate(item, start, end));
      return;
    }
    BookingPolicyException failure =
        assertThrows(BookingPolicyException.class, () -> policy.validate(item, start, end));
    assertEquals(
        switch (expected) {
          case "precision", "duration" -> BookingPolicyException.Reason.GRANULARITY;
          case "alignment" -> BookingPolicyException.Reason.ALIGNMENT;
          case "dayEndMissing" -> BookingPolicyException.Reason.DAY_END_MISSING;
          default -> throw new IllegalArgumentException("Unknown interval result " + expected);
        },
        failure.reason());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("settings")
  void settingsVector(String name, JsonNode vector) {
    BookingSchedulingSettings settings =
        new BookingSchedulingSettings(
            vector.get("slotGranularityMinutes").asLong(),
            BookingIncrementUnit.valueOf(vector.get("incrementUnit").asText()),
            vector.get("alignToIncrement").asBoolean(),
            BookingOpeningHoursCodec.openingHours(vector.get("openingHours")),
            0,
            0,
            vector.get("maxBookingDurationMinutes").asLong(),
            false);

    String expected = vector.get("expected").asText();
    if ("valid".equals(expected)) {
      assertDoesNotThrow(() -> BookingSettingsValidation.requireValid(settings));
      return;
    }
    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> BookingSettingsValidation.requireValid(settings));
    assertEquals(
        switch (expected) {
          case "granularity" -> InvalidBookingSchedulingSettingsException.Reason.GRANULARITY;
          case "maximumDuration" ->
              InvalidBookingSchedulingSettingsException.Reason.MAXIMUM_DURATION;
          default -> throw new IllegalArgumentException("Unknown settings result " + expected);
        },
        failure.reason());
  }

  private static Stream<Arguments> named(JsonNode vectors) {
    return StreamSupport.stream(vectors.spliterator(), false)
        .map(vector -> Arguments.of(vector.get("name").asText(), vector));
  }

  private static JsonNode load() {
    try (InputStream in =
        BookingIncrementScenariosTest.class.getResourceAsStream(
            "/booking/incrementScenarios.json")) {
      return new ObjectMapper().readTree(in);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
