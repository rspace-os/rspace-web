package com.researchspace.api.v2.controller;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.webapp.controller.MVCTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Exercises authentication, personal choices and committed subscription persistence over REST. */
class BookingNotificationSubscriptionControllerIT extends MVCTestBase {
  private static final String DEFAULTS = "/api/v2/users/me/booking-notification-preferences";
  private static final String SUBSCRIPTIONS = "/api/v2/users/me/booking-notification-subscriptions";
  @Autowired private FeatureFlagManager featureFlags;
  private ApiV2Fixture fixture;
  private boolean originallyEnabled;
  private static final String OPENAPI = "/api/v2/openapi.json";

  @BeforeEach
  void prepareNotifications() {
    fixture = ApiV2Fixture.in(wac);
    mockMvc = fixture.mockMvc();
    originallyEnabled =
        featureFlags
            .getFeatureFlag(BOOKING_ENABLED, getSysAdminUser())
            .orElseThrow()
            .isBaselineValue();
    setBookingEnabled(true);
  }

  @AfterEach
  void cleanNotifications() {
    setBookingEnabled(originallyEnabled);
    fixture.cleanUp();
  }

  @Test
  void requiresAuthenticationAndBookingFeature() throws Exception {
    mockMvc.perform(get(DEFAULTS)).andExpect(status().isUnauthorized());
    mockMvc.perform(delete(SUBSCRIPTIONS)).andExpect(status().isUnauthorized());
    setBookingEnabled(false);
    mockMvc
        .perform(get(DEFAULTS).header("apiKey", fixture.userKey()))
        .andExpect(status().isForbidden());
  }

  @Test
  void publishesNotificationOperationsInGeneratedOpenApi() throws Exception {
    mockMvc
        .perform(get(OPENAPI))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.paths['/api/v2/users/me/booking-notification-preferences'].get.operationId")
                .value("getMyBookingNotificationPreferences"))
        .andExpect(
            jsonPath("$.paths['/api/v2/users/me/booking-notification-preferences'].put.operationId")
                .value("replaceMyBookingNotificationPreferences"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v2/booking-configurations/{id}/notification-subscription'].get.operationId")
                .value("getMyBookingNotificationSubscription"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v2/booking-configurations/{id}/notification-subscription'].put.operationId")
                .value("replaceMyBookingNotificationSubscription"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v2/users/me/booking-notification-subscriptions/lookup'].post.operationId")
                .value("lookupMyBookingNotificationSubscriptions"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v2/users/me/booking-notification-subscriptions'].put.operationId")
                .value("replaceMyBookingNotificationSubscriptions"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v2/users/me/booking-notification-subscriptions'].delete.operationId")
                .value("unsubscribeMyBookingNotifications"));
  }

  @Test
  void readableAdminCanManageOnlyTheirOwnSubscription() throws Exception {
    long id =
        fixture.bookingConfiguration(
            fixture.instrument(fixture.user(), "Personal notifications"), "UTC");
    String item = itemPath(id);
    mockMvc
        .perform(get(item).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
    mockMvc
        .perform(get(item).header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.version").value(-1));
    mockMvc
        .perform(
            put(item)
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false,\"version\":-1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    mockMvc
        .perform(get(item).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true));
    mockMvc
        .perform(get("/api/v2/booking-configurations/" + id).header("apiKey", fixture.userKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.canManageNotificationSubscription").value(true));
    mockMvc
        .perform(
            get("/api/v2/booking-configurations/" + id).header("apiKey", fixture.sysadminKey()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.canManageNotificationSubscription").value(true));
  }

  @Test
  void snapshotsDefaultsPreservesOptOutAndDetectsConcurrentEdits() throws Exception {
    String key = fixture.userKey();
    long first =
        fixture.bookingConfiguration(
            fixture.instrument(fixture.user(), "Existing instrument"), "UTC");
    mockMvc
        .perform(
            put(DEFAULTS)
                .header("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"autoSubscribeOwnedItems\":false}"))
        .andExpect(status().isOk());
    long second =
        fixture.bookingConfiguration(fixture.instrument(fixture.user(), "New instrument"), "UTC");
    String json =
        mockMvc
            .perform(get(itemPath(first)).header("apiKey", key))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long version = JsonPath.parse(json).read("$.version", Long.class);
    mockMvc
        .perform(get(itemPath(second)).header("apiKey", key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    String change = "{\"enabled\":false,\"version\":" + version + "}";
    mockMvc
        .perform(
            put(itemPath(first))
                .header("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(change))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    mockMvc
        .perform(
            put(itemPath(first))
                .header("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(change))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            put(SUBSCRIPTIONS)
                .header("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"configurationIds\":[" + first + "," + second + "],\"enabled\":true}"))
        .andExpect(status().isOk());
    mockMvc.perform(delete(SUBSCRIPTIONS).header("apiKey", key)).andExpect(status().isOk());
    mockMvc
        .perform(get(itemPath(first)).header("apiKey", key))
        .andExpect(jsonPath("$.enabled").value(false));
    mockMvc
        .perform(get(itemPath(second)).header("apiKey", key))
        .andExpect(jsonPath("$.enabled").value(false));
    mockMvc
        .perform(get(DEFAULTS).header("apiKey", key))
        .andExpect(jsonPath("$.autoSubscribeOwnedItems").value(false));
  }

  private void setBookingEnabled(boolean enabled) {
    featureFlags
        .updateFeatureFlag(
            BOOKING_ENABLED,
            new FeatureFlagManager.Patch(enabled, false, null),
            getSysAdminUser(),
            getSysAdminUser())
        .orElseThrow();
  }

  private static String itemPath(long id) {
    return "/api/v2/booking-configurations/" + id + "/notification-subscription";
  }
}
