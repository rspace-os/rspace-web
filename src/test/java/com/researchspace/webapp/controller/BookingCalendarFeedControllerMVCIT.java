package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.booking.service.BookingCalendarManager;
import com.researchspace.model.User;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class BookingCalendarFeedControllerMVCIT {

  @Autowired private WebApplicationContext context;
  @Autowired private BookingCalendarManager calendarManager;

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
  void anonymousFeedDeliveryTracksReplacementAndRevocation() throws Exception {
    User owner = fixture.user();
    long instrumentId = fixture.instrument(owner, "Calendar " + fixture.marker());
    long configurationId = fixture.bookingConfiguration(instrumentId, "UTC");
    Instant candidate = Instant.now().plus(2, ChronoUnit.DAYS);
    Instant start = Instant.ofEpochSecond(((candidate.getEpochSecond() + 299) / 300) * 300);
    fixture.booking(instrumentId, start, start.plus(1, ChronoUnit.HOURS));

    String firstToken = token(calendarManager.createOrRotate(configurationId, owner, owner));
    assertAvailable(firstToken);

    String replacementToken = token(calendarManager.createOrRotate(configurationId, owner, owner));
    assertNotEquals(firstToken, replacementToken);
    assertMissing(firstToken);
    assertAvailable(replacementToken);

    calendarManager.revoke(configurationId, owner, owner);
    assertMissing(replacementToken);
  }

  private void assertAvailable(String token) throws Exception {
    mockMvc
        .perform(get(BookingCalendarFeedController.PATH).queryParam("token", token))
        .andExpect(status().isOk())
        .andExpect(content().contentType("text/calendar;charset=UTF-8"))
        .andExpect(header().string("Cache-Control", "private, no-store"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(content().string(org.hamcrest.Matchers.startsWith("BEGIN:VCALENDAR")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString(fixture.marker())))
        .andExpect(content().string(org.hamcrest.Matchers.endsWith("END:VCALENDAR\r\n")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<html"))));
  }

  private void assertMissing(String token) throws Exception {
    mockMvc
        .perform(get(BookingCalendarFeedController.PATH).queryParam("token", token))
        .andExpect(status().isNotFound())
        .andExpect(header().string("Cache-Control", "private, no-store"))
        .andExpect(content().bytes(new byte[0]));
  }

  private static String token(BookingCalendarManager.Created created) {
    String query = URI.create(created.subscriptionUrl()).getRawQuery();
    return query.substring("token=".length());
  }
}
