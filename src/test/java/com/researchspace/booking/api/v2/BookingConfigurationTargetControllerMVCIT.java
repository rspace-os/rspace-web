package com.researchspace.booking.api.v2;

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

  private final WebApplicationContext context;

  @Autowired
  BookingConfigurationTargetControllerMVCIT(WebApplicationContext context) {
    this.context = context;
  }

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    fixture.enableBookings();
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
                .queryParam("q", fixture.marker())
                .header("apiKey", fixture.userKey()))
        .andDo(BookingConfigurationTargetControllerMVCIT::failOnUnexpectedServerError)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[*].id", hasItem((int) owned)))
        .andExpect(jsonPath("$.docs[*].id", not(hasItem((int) somebodyElses))))
        .andExpect(jsonPath("$.docs[0].owner").doesNotExist());

    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("q", fixture.marker())
                .header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[*].id", hasItem((int) owned)))
        .andExpect(jsonPath("$.docs[*].id", hasItem((int) somebodyElses)));

    fixture.bookingConfiguration(owned, "UTC", fixture.userKey());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("q", fixture.marker())
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[*].id", not(hasItem((int) owned))));
  }

  @Test
  void browsingWithoutAQueryKeepsOwnershipAndConfiguredTargetRestrictions() throws Exception {
    User owner = fixture.user();
    long later = fixture.instrument(owner, "zz " + fixture.marker() + "-later");
    long earlier = fixture.instrument(owner, "aa " + fixture.marker() + "-earlier");
    long somebodyElses = fixture.instrument(fixture.otherUser(), fixture.marker() + "-other");

    for (String query : new String[] {null, "", "   "}) {
      var request =
          get("/api/v2/booking-configuration-targets")
              .queryParam("limit", "50")
              .header("apiKey", fixture.userKey());
      if (query != null) {
        request.queryParam("q", query);
      }
      mockMvc
          .perform(request)
          .andDo(BookingConfigurationTargetControllerMVCIT::failOnUnexpectedServerError)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs[*].id", hasItem((int) earlier)))
          .andExpect(jsonPath("$.docs[*].id", hasItem((int) later)))
          .andExpect(jsonPath("$.docs[*].id", not(hasItem((int) somebodyElses))));
    }
    java.util.List<Integer> browsed =
        com.jayway.jsonpath.JsonPath.read(
            mockMvc
                .perform(
                    get("/api/v2/booking-configuration-targets")
                        .queryParam("limit", "50")
                        .header("apiKey", fixture.userKey()))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.docs[*].id");
    org.junit.jupiter.api.Assertions.assertTrue(
        browsed.indexOf((int) earlier) < browsed.indexOf((int) later), browsed::toString);
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("limit", "1")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs.length()").value(1));

    fixture.bookingConfiguration(earlier, "UTC", fixture.userKey());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("limit", "50")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[*].id", not(hasItem((int) earlier))))
        .andExpect(jsonPath("$.docs[*].id", hasItem((int) later)));
  }

  @Test
  void validatesAuthenticationQueryAndLimit() throws Exception {
    mockMvc
        .perform(get("/api/v2/booking-configuration-targets").queryParam("q", "ab"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("q", "x")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("q", "ab")
                .queryParam("limit", "51")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("limit", "0")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/v2/booking-configuration-targets"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void globalIdSearchKeepsOwnershipAndConfiguredTargetRestrictions() throws Exception {
    long owned = fixture.instrument(fixture.user(), fixture.marker() + "-owned");
    long other = fixture.instrument(fixture.otherUser(), fixture.marker() + "-other");
    for (String prefix : new String[] {"IN", "in"}) {
      mockMvc
          .perform(
              get("/api/v2/booking-configuration-targets")
                  .queryParam("q", prefix + owned)
                  .header("apiKey", fixture.userKey()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.docs[*].id", hasItem((int) owned)));
    }
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("q", "IN" + other)
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isEmpty());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("q", "IN" + other)
                .header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs[*].id", hasItem((int) other)));
    fixture.bookingConfiguration(owned, "UTC", fixture.userKey());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("q", "IN" + owned)
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isEmpty());
    mockMvc
        .perform(
            get("/api/v2/booking-configuration-targets")
                .queryParam("q", "IN" + "9".repeat(30))
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.docs").isEmpty());
  }

  private static void failOnUnexpectedServerError(
      org.springframework.test.web.servlet.MvcResult result) {
    if (result.getResponse().getStatus() >= 500 && result.getResolvedException() != null) {
      throw new AssertionError("Unexpected server error", result.getResolvedException());
    }
  }
}
