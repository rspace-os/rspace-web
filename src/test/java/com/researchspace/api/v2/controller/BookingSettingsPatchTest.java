package com.researchspace.api.v2.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.controller.BookingSettingsController.SettingsPatch;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException;
import com.researchspace.booking.service.InvalidBookingSchedulingSettingsException.Reason;
import com.researchspace.model.booking.BookingOpeningException;
import com.researchspace.model.booking.BookingSchedulingSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Jackson binding of the defaults endpoint, using Jackson's permissive default coercion. */
class BookingSettingsPatchTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private BookingSchedulingSettings.Patch patch(String json) throws Exception {
    return mapper.readValue(json, SettingsPatch.class).schedulingPatch();
  }

  @Test
  void decodesBothFieldsSortedAndTreatsOmissionAndNullAsNoChange() throws Exception {
    BookingSchedulingSettings.Patch supplied =
        patch(
            """
            {"openDays":[6,1],"openingExceptions":[{"dayOfWeek":6,"start":"10:00","end":"16:00"}],
             "configurationVersion":0}
            """);

    assertEquals(List.of(1, 6), supplied.openDays());
    assertEquals(
        List.of(new BookingOpeningException(6, "10:00", "16:00")), supplied.openingExceptions());
    BookingSchedulingSettings.Patch omitted = patch("{\"configurationVersion\":0}");
    assertNull(omitted.openDays());
    assertNull(omitted.openingExceptions());
    assertTrue(omitted.isEmpty());
    BookingSchedulingSettings.Patch nulls =
        patch("{\"openDays\":null,\"openingExceptions\":null,\"configurationVersion\":0}");
    assertTrue(nulls.isEmpty());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[\"1\"]", "[1.5]", "[1.0]", "[true]", "[null]", "[]", "[1,1]", "[8]", "\"1\"", "1"
      })
  void rejectsCoercibleOrInvalidOpenDays(String value) {
    assertEquals(Reason.OPEN_DAYS, rejection("{\"openDays\":" + value + "}"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[{\"dayOfWeek\":\"6\",\"start\":\"10:00\",\"end\":\"16:00\"}]",
        "[{\"dayOfWeek\":6,\"start\":\"10:00\",\"end\":\"16:00\",\"extra\":true}]",
        "[{\"dayOfWeek\":6,\"start\":\"10:00\"}]",
        "[{\"dayOfWeek\":6,\"start\":1000,\"end\":\"16:00\"}]",
        "[null]",
        "{}"
      })
  void rejectsCoercibleOrInvalidExceptions(String value) {
    assertEquals(Reason.OPENING_EXCEPTIONS, rejection("{\"openingExceptions\":" + value + "}"));
  }

  private Reason rejection(String json) {
    return assertThrows(InvalidBookingSchedulingSettingsException.class, () -> patch(json))
        .reason();
  }
}
