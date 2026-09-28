package com.researchspace.booking.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.resource.ApiV2ResourceException;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.booking.service.BookingBufferConflictException;
import com.researchspace.booking.service.BookingOverlapException;
import com.researchspace.booking.service.BookingPolicyException;
import com.researchspace.booking.service.ConflictingEvent;
import com.researchspace.booking.service.TimeSlotBookingManager;
import com.researchspace.model.booking.ApiV2TimeSlotBookingResource;
import com.researchspace.model.booking.BookingEventKind;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;

/** Pins the booking scheduling problem bodies that clients render. */
class TimeSlotBookingProblemContractTest {

  private static final ConflictingEvent BLOCKING =
      new ConflictingEvent(
          59L,
          BookingEventKind.BOOKING,
          Instant.parse("2026-09-28T08:00:00Z"),
          Instant.parse("2026-09-28T10:00:00Z"));

  private final ObjectMapper json = new ObjectMapper();
  private final TimeSlotBookingResourceOperations operations =
      new TimeSlotBookingResourceOperations(
          mock(TimeSlotBookingManager.class),
          mock(FeatureFlagManager.class),
          ApiV2TimeSlotBookingResource.DESCRIPTION);
  private ApiV2ControllerAdvice advice;

  @BeforeEach
  void setUp() {
    LocaleContextHolder.setLocale(Locale.US);
    MessageSourceUtils messages = new MessageSourceUtils();
    messages.setMessageSource(new JsonMessageSource());
    advice = new ApiV2ControllerAdvice(messages);
  }

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void overlapIdentifiesTheBlockingEventWithoutPrivateFields() throws Exception {
    JsonNode body =
        body(advice.handleResourceException(translate(new BookingOverlapException(BLOCKING))));

    assertEquals(
        json.readTree(
            """
            {"title":"The selected time overlaps another booking.","status":409,
             "code":"errors.api.v2.booking.overlap",
             "detail":"The selected time overlaps another booking.",
             "conflict":{"id":59,"kind":"BOOKING",
               "start":"2026-09-28T08:00:00Z","end":"2026-09-28T10:00:00Z"}}
            """),
        body);
  }

  @Test
  void bufferIdentifiesTheBlockingEventAndBothBuffers() throws Exception {
    JsonNode body =
        body(
            advice.handleResourceException(
                translate(new BookingBufferConflictException(BLOCKING, 15, 30))));

    assertEquals(
        json.readTree(
            """
            {"title":"The selected time is too close to another booking (buffer).","status":409,
             "code":"errors.api.v2.booking.buffer",
             "detail":"The selected time is too close to another booking (buffer).",
             "conflict":{"id":59,"kind":"BOOKING",
               "start":"2026-09-28T08:00:00Z","end":"2026-09-28T10:00:00Z"},
             "bufferBeforeMinutes":15,"bufferAfterMinutes":30}
            """),
        body);
  }

  @Test
  void maximumDurationStatesTheLimitInTheDetailAndAsAMember() throws Exception {
    JsonNode body = body(advice.handleBookingPolicy(BookingPolicyException.maximumDuration(90)));

    assertEquals(
        json.readTree(
            """
            {"title":"The booking exceeds this bookable item's maximum duration of 90 minutes.",
             "status":400,"code":"errors.api.v2.booking.maximumDuration",
             "detail":"The booking exceeds this bookable item's maximum duration of 90 minutes.",
             "maximumDurationMinutes":90}
            """),
        body);
  }

  @Test
  void otherSchedulingPolicyProblemsHaveNoExtensionMembers() throws Exception {
    JsonNode body =
        body(
            advice.handleBookingPolicy(
                new BookingPolicyException(BookingPolicyException.Reason.GRANULARITY)));

    assertEquals(
        json.readTree(
            """
            {"title":"Start and end must align with this bookable item's slot granularity.",
             "status":400,"code":"errors.api.v2.booking.granularity",
             "detail":"Start and end must align with this bookable item's slot granularity."}
            """),
        body);
  }

  private ApiV2ResourceException translate(RuntimeException failure) {
    return operations
        .timeSlotBookingApiV2Resource()
        .errorMappings()
        .get(ResourceOperation.CREATE)
        .stream()
        .filter(mapping -> mapping.exceptionType() == failure.getClass())
        .findFirst()
        .orElseThrow()
        .translate(failure);
  }

  /** Round-trips through text so numbers compare by value, as a client would parse them. */
  private JsonNode body(ResponseEntity<ApiV2Problem> response) throws Exception {
    return json.readTree(json.writeValueAsString(response.getBody()));
  }
}
