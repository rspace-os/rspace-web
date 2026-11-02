package com.researchspace.booking.api.v2;

import static com.researchspace.testutils.BookingResourceTestFixtures.translate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v2.controller.ApiV2ControllerAdvice;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.booking.service.BookingBufferConflictException;
import com.researchspace.booking.service.BookingLocationFilterManager;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
  private TimeSlotBookingResourceOperations operations;
  private BookingApiV2ControllerAdvice advice;

  @BeforeEach
  void setUp() {
    LocaleContextHolder.setLocale(Locale.US);
    MessageSourceUtils messages = new MessageSourceUtils();
    messages.setMessageSource(new JsonMessageSource());
    advice = new BookingApiV2ControllerAdvice(messages);
    operations =
        new TimeSlotBookingResourceOperations(
            mock(TimeSlotBookingManager.class),
            mock(FeatureFlagManager.class),
            ApiV2TimeSlotBookingResource.DESCRIPTION,
            messages,
            mock(BookingLocationFilterManager.class));
  }

  @AfterEach
  void resetLocale() {
    LocaleContextHolder.resetLocaleContext();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("schedulingProblemBodies")
  void schedulingProblemBodiesRemainStable(
      String scenario, RuntimeException failure, boolean policy, String expectedJson)
      throws Exception {
    ResponseEntity<ApiV2Problem> response =
        policy
            ? advice.handleBookingPolicy((BookingPolicyException) failure)
            : advice.handleResourceException(
                translate(
                    operations.timeSlotBookingApiV2Resource(), ResourceOperation.CREATE, failure));

    assertEquals(json.readTree(expectedJson), body(response));
    if (policy) {
      assertEquals(
          json.readTree(expectedJson),
          body(
              new ApiV2ControllerAdvice(messageSource())
                  .handleResourceException(
                      translate(
                          operations.timeSlotBookingApiV2Resource(),
                          ResourceOperation.CREATE,
                          failure))));
    }
  }

  private static MessageSourceUtils messageSource() {
    MessageSourceUtils messages = new MessageSourceUtils();
    messages.setMessageSource(new JsonMessageSource());
    return messages;
  }

  private static Stream<Arguments> schedulingProblemBodies() {
    return Stream.of(
        Arguments.of(
            "overlap identifies only the blocking event",
            new BookingOverlapException(BLOCKING),
            false,
            """
            {"title":"The selected time overlaps another booking.","status":409,
             "code":"errors.api.v2.booking.overlap",
             "detail":"The selected time overlaps another booking.",
             "conflict":{"id":59,"kind":"BOOKING",
               "start":"2026-09-28T08:00:00Z","end":"2026-09-28T10:00:00Z"}}
            """),
        Arguments.of(
            "buffer conflict includes both buffers",
            new BookingBufferConflictException(BLOCKING, 15, 30),
            false,
            """
            {"title":"The selected time is too close to another booking (buffer).","status":409,
             "code":"errors.api.v2.booking.buffer",
             "detail":"The selected time is too close to another booking (buffer).",
             "conflict":{"id":59,"kind":"BOOKING",
               "start":"2026-09-28T08:00:00Z","end":"2026-09-28T10:00:00Z"},
             "bufferBeforeMinutes":15,"bufferAfterMinutes":30}
            """),
        Arguments.of(
            "maximum duration is stated in the detail and member",
            BookingPolicyException.maximumDuration(90),
            true,
            """
            {"title":"The booking exceeds this bookable item's maximum duration of 1 hour, 30 minutes.",
             "status":400,"code":"errors.api.v2.booking.maximumDuration",
             "detail":"The booking exceeds this bookable item's maximum duration of 1 hour, 30 minutes.",
             "maximumDurationMinutes":90}
            """),
        Arguments.of(
            "granularity policy has no extension members",
            new BookingPolicyException(BookingPolicyException.Reason.GRANULARITY),
            true,
            """
            {"title":"The booking must start and end on whole minutes and last a whole number of this bookable item's time increments. Day increments must end at the same local time as they start.",
             "status":400,"code":"errors.api.v2.booking.granularity",
             "detail":"The booking must start and end on whole minutes and last a whole number of this bookable item's time increments. Day increments must end at the same local time as they start."}
            """));
  }

  @Test
  void aMissingDayEndNamesTheLocalTimeDateAndZoneInTheDetail() throws Exception {
    JsonNode body =
        body(
            advice.handleBookingPolicy(
                BookingPolicyException.dayEndMissing(
                    LocalTime.of(2, 30), LocalDate.of(2026, 3, 29), ZoneId.of("Europe/Berlin"))));

    assertEquals(
        json.readTree(
            """
            {"title":"This booking would end at 02:30 on March 29, 2026, but that local time does not exist in Europe/Berlin. Change the start time or choose another duration.",
             "status":400,"code":"errors.api.v2.booking.dayEndMissing",
             "detail":"This booking would end at 02:30 on March 29, 2026, but that local time does not exist in Europe/Berlin. Change the start time or choose another duration."}
            """),
        body);
  }

  @Test
  void missingDayEndHasTheSameBodyThroughCollectionOperations() throws Exception {
    BookingPolicyException failure =
        BookingPolicyException.dayEndMissing(
            LocalTime.of(2, 30), LocalDate.of(2026, 3, 29), ZoneId.of("Europe/Berlin"));
    assertEquals(
        body(advice.handleBookingPolicy(failure)),
        body(
            advice.handleResourceException(
                translate(
                    operations.timeSlotBookingApiV2Resource(),
                    ResourceOperation.CREATE,
                    failure))));
  }

  /** Round-trips through text so numbers compare by value, as a client would parse them. */
  private JsonNode body(ResponseEntity<ApiV2Problem> response) throws Exception {
    return json.readTree(json.writeValueAsString(response.getBody()));
  }
}
