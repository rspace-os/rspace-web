package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.model.User;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.testutils.ApiV2WebIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

@ApiV2WebIntegrationTest
class BookingCatalogueControllerMVCIT {

  @Autowired private WebApplicationContext context;
  @Autowired private FeatureFlagManager featureFlags;
  @Autowired private UserManager userManager;

  private ApiV2Fixture fixture;
  private MockMvc mockMvc;
  private boolean originalBookingBaseline;

  @BeforeEach
  void setUp() {
    fixture = ApiV2Fixture.in(context);
    mockMvc = fixture.mockMvc();
    User sysadmin = sysadmin();
    originalBookingBaseline =
        featureFlags.getFeatureFlag(BOOKING_ENABLED, sysadmin).orElseThrow().isBaselineValue();
    setBookingEnabled(true);
  }

  @AfterEach
  void tearDown() {
    fixture.cleanUp();
    setBookingEnabled(originalBookingBaseline);
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v2/booking-catalogue")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v2/booking-catalogue/locations"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void discoversConfiguredItemsWithoutEventsAndPagesInTheDatabase() throws Exception {
    long firstInstrument = fixture.instrument(fixture.user(), "Alpha scope " + fixture.marker());
    long secondInstrument = fixture.instrument(fixture.user(), "Beta scope " + fixture.marker());
    fixture.bookingConfiguration(firstInstrument, "UTC", fixture.userKey());
    fixture.bookingConfiguration(secondInstrument, "UTC", fixture.userKey());

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("q", fixture.marker())
                .queryParam("page", "1")
                .queryParam("limit", "1")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].name").value("Alpha scope " + fixture.marker()));

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("q", fixture.marker())
                .queryParam("page", "2")
                .queryParam("limit", "1")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.items[0].name").value("Beta scope " + fixture.marker()));
  }

  @Test
  void filtersByExactReadableImmediateParentAndPagesLocationOptions() throws Exception {
    ApiContainer parent = fixture.container(fixture.user(), "Imaging lab " + fixture.marker());
    String parentGlobalId = "IC" + parent.getId();
    long inParent = fixture.instrumentIn(fixture.user(), "Confocal " + fixture.marker(), parent);
    long outside = fixture.instrument(fixture.user(), "Centrifuge " + fixture.marker());
    fixture.bookingConfiguration(inParent, "UTC", fixture.userKey());
    fixture.bookingConfiguration(outside, "UTC", fixture.userKey());

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("location", parentGlobalId)
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].globalId").value("IN" + inParent))
        .andExpect(jsonPath("$.items[0].location.globalId").value(parentGlobalId));

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue/locations")
                .queryParam("q", "Imaging lab")
                .queryParam("limit", "1")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].globalId").value(parentGlobalId));

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("location", "IC999999999")
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items").isEmpty());

    mockMvc
        .perform(
            get("/api/v2/booking-catalogue")
                .queryParam("location", "BE" + parent.getId())
                .header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void catalogueIsHiddenWhenBookingIsDisabled() throws Exception {
    setBookingEnabled(false);

    mockMvc
        .perform(get("/api/v2/booking-catalogue").header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v2/booking-catalogue/locations").header("apiKey", fixture.userKey()))
        .andExpect(status().isNotFound());

    setBookingEnabled(true);
  }

  private User sysadmin() {
    return userManager.getUserByUsername(AbstractAppInitializor.SYSADMIN_UNAME);
  }

  private void setBookingEnabled(boolean enabled) {
    User sysadmin = sysadmin();
    featureFlags
        .updateFeatureFlag(
            BOOKING_ENABLED, new FeatureFlagManager.Patch(enabled, false, null), sysadmin, sysadmin)
        .orElseThrow();
  }
}
