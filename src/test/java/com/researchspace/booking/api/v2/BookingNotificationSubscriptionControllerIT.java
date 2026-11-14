package com.researchspace.booking.api.v2;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.booking.service.BookingNotificationSubscriptionManager;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.testutils.ApiV2Fixture;
import com.researchspace.webapp.controller.MVCTestBase;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Exercises authentication, personal choices and committed subscription persistence over REST. */
class BookingNotificationSubscriptionControllerIT extends MVCTestBase {
  private static final String DEFAULTS = "/api/v2/users/me/booking-notification-preferences";
  private static final String SUBSCRIPTIONS = "/api/v2/users/me/booking-notification-subscriptions";
  private final FeatureFlagManager featureFlags;

  @Autowired
  BookingNotificationSubscriptionControllerIT(FeatureFlagManager featureFlags) {
    this.featureFlags = featureFlags;
  }

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
        .andExpect(status().isNotFound());
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
                .doesNotExist())
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
        .andExpect(jsonPath("$.version").doesNotExist());
    mockMvc
        .perform(
            put(item)
                .header("apiKey", fixture.sysadminKey())
                .contentType(MediaType.APPLICATION_JSON)
                .header("If-Match", "\"vabsent-000\"")
                .content("{\"enabled\":false}"))
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
                .content(
                    "{\"autoSubscribeOwnedItems\":false,\"notifyOwnActions\":true,\"notifyOnCreated\":true,"
                        + "\"notifyOnCancelled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autoSubscribeOwnedItems").value(false))
        .andExpect(jsonPath("$.notifyOnCreated").value(true))
        .andExpect(jsonPath("$.notifyOnCancelled").value(false))
        .andExpect(jsonPath("$.emailDelivery").isBoolean());
    long second =
        fixture.bookingConfiguration(fixture.instrument(fixture.user(), "New instrument"), "UTC");
    String etag =
        mockMvc
            .perform(get(itemPath(first)).header("apiKey", key))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    mockMvc
        .perform(get(itemPath(second)).header("apiKey", key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    String change = JacksonUtil.toJson(Map.of("enabled", false));
    mockMvc
        .perform(
            put(itemPath(first))
                .header("If-Match", etag)
                .header("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(change))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    mockMvc
        .perform(
            put(itemPath(first))
                .header("If-Match", etag)
                .header("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(change))
        .andExpect(status().isPreconditionFailed());
    etag =
        mockMvc
            .perform(get(itemPath(first)).header("apiKey", key))
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    mockMvc
        .perform(
            put(itemPath(first))
                .header("If-Match", etag)
                .header("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JacksonUtil.toJson(Map.of("enabled", true))))
        .andExpect(status().isOk());
    // The event choices are the My Profile preferences that gate each item's delivery.
    mockMvc
        .perform(get(itemPath(first)).header("apiKey", key))
        .andExpect(jsonPath("$.createdEnabled").value(true))
        .andExpect(jsonPath("$.cancelledEnabled").value(false));
    mockMvc.perform(delete(SUBSCRIPTIONS).header("apiKey", key)).andExpect(status().isOk());
    mockMvc
        .perform(get(itemPath(first)).header("apiKey", key))
        .andExpect(jsonPath("$.enabled").value(false));
    mockMvc
        .perform(get(itemPath(second)).header("apiKey", key))
        .andExpect(jsonPath("$.enabled").value(false));
    mockMvc
        .perform(get(DEFAULTS).header("apiKey", key))
        .andExpect(jsonPath("$.autoSubscribeOwnedItems").value(false))
        .andExpect(jsonPath("$.notifyOnCreated").value(true))
        .andExpect(jsonPath("$.notifyOnCancelled").value(false));
    mockMvc
        .perform(
            put(DEFAULTS)
                .header("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"autoSubscribeOwnedItems\":false}"))
        .andExpect(status().isBadRequest());
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

  @Test
  void preferenceServiceChangesInvalidateTheFullSubscriptionTag() throws Exception {
    long id =
        fixture.bookingConfiguration(
            fixture.instrument(fixture.user(), "Preference validator"), "UTC");
    String key = fixture.userKey();
    var manager = wac.getBean(BookingNotificationSubscriptionManager.class);
    manager.replacePreferences(
        new BookingNotificationSubscriptionManager.PreferenceChanges(true, true, true, true),
        fixture.user(),
        fixture.user());
    String tag =
        mockMvc
            .perform(get(itemPath(id)).header("apiKey", key))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    assertTrue(tag.endsWith("-111\""));
    manager.replacePreferences(
        new BookingNotificationSubscriptionManager.PreferenceChanges(true, true, false, true),
        fixture.user(),
        fixture.user());
    mockMvc
        .perform(
            put(itemPath(id))
                .header("apiKey", key)
                .header("If-Match", tag)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isPreconditionFailed());
  }

  @Test
  void simultaneousFirstWritesAllowOnlyOneAbsentTag() throws Exception {
    long id =
        fixture.bookingConfiguration(
            fixture.instrument(fixture.user(), "Concurrent first subscription"), "UTC");
    String key = fixture.sysadminKey();
    String tag =
        mockMvc
            .perform(get(itemPath(id)).header("apiKey", key))
            .andReturn()
            .getResponse()
            .getHeader("ETag");
    var ready = new CountDownLatch(2);
    var workers = Executors.newFixedThreadPool(2);
    Callable<Integer> write =
        () -> {
          ready.countDown();
          if (!ready.await(15, TimeUnit.SECONDS))
            throw new IllegalStateException("Writers did not start");
          return mockMvc
              .perform(
                  put(itemPath(id))
                      .header("apiKey", key)
                      .header("If-Match", tag)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"enabled\":true}"))
              .andReturn()
              .getResponse()
              .getStatus();
        };
    try {
      var first = workers.submit(write);
      var second = workers.submit(write);
      assertEquals(
          Set.of(200, 412),
          Set.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)));
    } finally {
      workers.shutdownNow();
    }
  }

  private static String itemPath(long id) {
    return "/api/v2/booking-configurations/" + id + "/notification-subscription";
  }
}
