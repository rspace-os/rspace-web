package com.researchspace.api.v2.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class BookingConfigurationTargetControllerMVCIT {

  @Autowired private WebApplicationContext context;
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
  void searchIsBoundedToEligibleOwnedTargetsAndSysadminCanSeeAll() throws Exception {
    User owner = fixture.user();
    long owned = fixture.instrument(owner, fixture.marker() + "-owned");
    long somebodyElses = fixture.instrument(fixture.otherUser(), fixture.marker() + "-other");

    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("query", fixture.marker())
                .header("apiKey", fixture.userKey()))
        .andDo(BookingConfigurationTargetControllerMVCIT::failOnUnexpectedServerError)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem((int) owned)))
        .andExpect(jsonPath("$[*].id", not(hasItem((int) somebodyElses))))
        .andExpect(jsonPath("$[0].owner").doesNotExist());

    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("query", fixture.marker())
                .header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", hasItem((int) owned)))
        .andExpect(jsonPath("$[*].id", hasItem((int) somebodyElses)));

    fixture.bookingConfiguration(owned, "UTC", fixture.userKey());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("query", fixture.marker())
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id", not(hasItem((int) owned))));
  }

  @Test
  void validatesAuthenticationQueryAndLimit() throws Exception {
    mockMvc
        .perform(get("/api/v2/booking-configuration-targets").queryParam("query", "ab"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("query", "x")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("query", "ab")
                .queryParam("limit", "51")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
  }

  private static void failOnUnexpectedServerError(
      org.springframework.test.web.servlet.MvcResult result) {
    if (result.getResponse().getStatus() >= 500 && result.getResolvedException() != null) {
      throw new AssertionError("Unexpected server error", result.getResolvedException());
    }
  }
}
