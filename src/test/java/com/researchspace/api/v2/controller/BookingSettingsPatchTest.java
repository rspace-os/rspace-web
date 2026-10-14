package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.controller.BookingSettingsController.SettingsPatch;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BookingSettingsPatchTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void decodesTheCompleteWeekInOrderAndTreatsOmissionAndNullAsNoChange() throws Exception {
    BookingSchedulingSettings.Patch supplied =
        patch(
            """
            {"openingHours":[{"dayOfWeek":6,"start":"10:00","end":"16:00"},
                             {"dayOfWeek":1,"start":"09:00","end":"17:00"}],
             "configurationVersion":0}
            """);
    assertEquals(
        List.of(
            new BookingOpeningHoursEntry(1, "09:00", "17:00"),
            new BookingOpeningHoursEntry(6, "10:00", "16:00")),
        supplied.openingHours());
    BookingSchedulingSettings current =
        BookingSchedulingSettings.from(new BookingConfigurationDefaults());
    for (String json :
        List.of(
            "{\"configurationVersion\":0}", "{\"openingHours\":null,\"configurationVersion\":0}")) {
      BookingSchedulingSettings.Patch unchanged = patch(json);
      assertTrue(unchanged.isEmpty());
      assertEquals(current, unchanged.merge(current));
    }
    BookingConfigurationDefaults defaults = new BookingConfigurationDefaults();
    supplied.merge(current).applyTo(defaults);
    Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC"));
    for (Object document :
        List.of(
            BookingSettingsController.SettingsDocument.from(defaults, clock),
            BookingSettingsController.AdminSettingsDocument.from(defaults, clock))) {
      JsonNode rendered = mapper.valueToTree(document);
      assertEquals(mapper.valueToTree(supplied.openingHours()), rendered.get("openingHours"));
      assertFalse(rendered.has("openingStart"));
      assertFalse(rendered.has("openingEnd"));
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "[null]",
        "[1]",
        "{}",
        "[\"1\"]",
        "[1.5]",
        "[1.0]",
        "[true]",
        "[1,1]",
        "[8]",
        "\"1\"",
        "1",
        "[{\"dayOfWeek\":\"6\",\"start\":\"10:00\",\"end\":\"16:00\"}]",
        "[{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\",\"extra\":true}]",
        "[{\"dayOfWeek\":6,\"start\":\"10:00\"}]",
        "[{\"dayOfWeek\":6,\"start\":1000,\"end\":\"16:00\"}]"
      })
  void rejectsInvalidValuesBeforeJacksonCanCoerceRowChildren(String value) {
    assertEquals(
        Reason.OPENING_HOURS,
        assertThrows(
                InvalidBookingSchedulingSettingsException.class,
                () -> patch("{\"openingHours\":" + value + ",\"configurationVersion\":0}"))
            .reason());
  }

  private BookingSchedulingSettings.Patch patch(String json) throws Exception {
    return mapper.readValue(json, SettingsPatch.class).schedulingPatch();
  }
}
