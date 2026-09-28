package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/** Pins the scheduling problem bodies that the booking form renders, through the real stack. */
@ApiV2WebIntegrationTest
class TimeSlotBookingConflictMVCIT {

  @Autowired private WebApplicationContext context;

  @Autowired
  @Qualifier("bookingConfigurationDao")
  private BookingConfigurationDao configurationDao;

  @Autowired private PlatformTransactionManager transactionManager;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
  }

  @Test
  void overlapAndBufferProblemsIdentifyOnlyThePublicIntervalOfTheBlockingEvent() throws Exception {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    Instant start = alignedStart();
    Instant end = start.plus(2, ChronoUnit.HOURS);
    long blocking = fixture.booking(instrumentId, start, end);

    create(instrumentId, start.plus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(ApiV2Problem.PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("errors.api.v2.booking.overlap"))
        .andExpect(jsonPath("$.conflict.id").value(blocking))
        .andExpect(jsonPath("$.conflict.kind").value("BOOKING"))
        .andExpect(jsonPath("$.conflict.start").value(start.toString()))
        .andExpect(jsonPath("$.conflict.end").value(end.toString()))
        .andExpect(jsonPath("$.conflict.purpose").doesNotExist())
        .andExpect(jsonPath("$.conflict.requester").doesNotExist())
        .andExpect(jsonPath("$.bufferBeforeMinutes").doesNotExist());

    updateConfiguration(
        configurationId,
        configuration -> {
          configuration.setBufferBeforeMinutes(15);
          configuration.setBufferAfterMinutes(30);
        });

    create(instrumentId, end.plus(10, ChronoUnit.MINUTES), end.plus(1, ChronoUnit.HOURS))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("errors.api.v2.booking.buffer"))
        .andExpect(
            jsonPath("$.detail")
                .value("The selected time is too close to another booking (buffer)."))
        .andExpect(jsonPath("$.conflict.id").value(blocking))
        .andExpect(jsonPath("$.conflict.start").value(start.toString()))
        .andExpect(jsonPath("$.conflict.end").value(end.toString()))
        .andExpect(jsonPath("$.bufferBeforeMinutes").value(15))
        .andExpect(jsonPath("$.bufferAfterMinutes").value(30));
  }

  @Test
  void maximumDurationProblemStatesTheLimit() throws Exception {
    long instrumentId = fixture.instrument(fixture.user(), fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC", fixture.userKey());
    updateConfiguration(
        configurationId, configuration -> configuration.setMaxBookingDurationMinutes(60));
    Instant start = alignedStart();

    create(instrumentId, start, start.plus(2, ChronoUnit.HOURS))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("errors.api.v2.booking.maximumDuration"))
        .andExpect(jsonPath("$.detail").value(containsString("maximum duration of 60 minutes")))
        .andExpect(jsonPath("$.maximumDurationMinutes").value(60))
        .andExpect(jsonPath("$.conflict").doesNotExist());
  }

  private ResultActions create(long instrumentId, Instant start, Instant end) throws Exception {
    return mockMvc.perform(
        post("/api/v2/bookings")
            .header("apiKey", fixture.userKey())
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"target":{"relationTo":"booking-instruments","value":%d},\
                "start":"%s","end":"%s"}\
                """
                    .formatted(instrumentId, start, end)));
  }

  private void updateConfiguration(long configurationId, Consumer<BookingConfiguration> change) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(ignored -> change.accept(configurationDao.get(configurationId)));
  }

  private static Instant alignedStart() {
    return Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  }
}
